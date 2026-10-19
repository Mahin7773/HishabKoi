package com.example.hishabkoi.cloud;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Everything that touches Firestore. Methods block, so call them through {@code Bg.run}.
 *
 * Collections:
 *   usernames/{username}  -> {uid}                         unique username reservation
 *   users/{uid}           -> {username, email, createdAt}
 *   txns/{id}             -> loan events, see {@link Txn}
 *   debts/{uidA_uidB}     -> running balance per pair, see {@link Debt}
 *   proposals/{cycleKey}  -> circular settlements awaiting agreement, see {@link Proposal}
 *
 * Personal income/expenses never come here; they stay in the local Room database.
 */
public final class Cloud {
    public static final String OUTCOME_PENDING = "PENDING";
    public static final String OUTCOME_APPLIED = "APPLIED";
    public static final String OUTCOME_STALE = "STALE";

    /**
     * Search limits for loop detection, to keep Firestore reads small. MAX_CYCLE_LENGTH must match
     * the largest loop firestore.rules accepts (8): settling checks one debt read per edge, and
     * Firestore allows 10 reads per document.
     */
    private static final int MAX_CYCLE_LENGTH = 8;
    private static final int MAX_NODES = 60;
    private static final int MAX_CYCLES = 10;
    private static final long TIMEOUT_S = 20;

    private Cloud() {}

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static CollectionReference txns() {
        return db().collection("txns");
    }

    private static DocumentReference debtRef(String a, String b) {
        return db().collection("debts").document(a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a);
    }

    static long longOf(Long v) {
        return v == null ? 0 : v;
    }

    private static <T> T await(Task<T> task) throws Exception {
        return Tasks.await(task, TIMEOUT_S, TimeUnit.SECONDS);
    }

    private static FirebaseFirestoreException fail(String message) {
        return new FirebaseFirestoreException(message, FirebaseFirestoreException.Code.FAILED_PRECONDITION);
    }

    // ---------------------------------------------------------------- users

    public static boolean isUsernameTaken(String username) throws Exception {
        return await(db().collection("usernames").document(username).get()).exists();
    }

    /** Atomically claims the username and creates the profile. Fails if someone else owns it. */
    public static void reserveUsername(String uid, String username, String email) throws Exception {
        DocumentReference nameRef = db().collection("usernames").document(username);
        DocumentReference userRef = db().collection("users").document(uid);
        await(db().runTransaction(tr -> {
            DocumentSnapshot existing = tr.get(nameRef);
            if (existing.exists() && !uid.equals(existing.getString("uid"))) {
                throw fail("This username is taken");
            }
            tr.set(nameRef, Collections.singletonMap("uid", uid));
            Map<String, Object> user = new HashMap<>();
            user.put("username", username);
            user.put("email", email);
            user.put("createdAt", System.currentTimeMillis());
            tr.set(userRef, user);
            return null;
        }));
    }

    /** Username for a uid, or null if the profile was never created. */
    public static String usernameOf(String uid) throws Exception {
        return await(db().collection("users").document(uid).get()).getString("username");
    }

    /** uid for a username, or null if nobody has it. */
    public static String uidOf(String username) throws Exception {
        return await(db().collection("usernames").document(username).get()).getString("uid");
    }

    // ---------------------------------------------------------------- borrow / repay requests

    /** I (borrower) ask {@code lender} to lend me money, or tell them I paid them back. */
    public static void createRequest(String type, String me, String myName, String lenderUid,
                                     String lenderName, long amount, String note) throws Exception {
        long now = System.currentTimeMillis();
        await(txns().document().set(txnMap(type, me, myName, lenderUid, lenderName, amount, note,
                Txn.PENDING, me, lenderUid, now, null)));
    }

    /** The lender accepts or declines. Accepting updates the pair's balance in the same transaction. */
    public static void respond(String txnId, String me, boolean accept) throws Exception {
        DocumentReference ref = txns().document(txnId);
        await(db().runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(ref);
            Txn t = Txn.from(snap);
            if (!t.isPending()) throw fail("This request was already handled");
            if (!me.equals(t.respondTo)) throw fail("Only @" + t.lenderName + " can answer this request");
            long now = System.currentTimeMillis();
            if (accept) {
                DocumentReference dRef = debtRef(t.borrowerUid, t.lenderUid);
                Debt debt = Debt.from(tr.get(dRef));
                List<String> parts = new ArrayList<>(Arrays.asList(t.borrowerUid, t.lenderUid));
                Collections.sort(parts);
                long owed = debt == null ? 0 : debt.owedBy(t.borrowerUid);
                long newOwed = owed + (Txn.BORROW.equals(t.type) ? t.amount : -t.amount);
                long oldNet = debt == null ? 0 : debt.net;
                Map<String, Object> names = new HashMap<>();
                names.put(t.borrowerUid, t.borrowerName);
                names.put(t.lenderUid, t.lenderName);
                Map<String, Object> d = new HashMap<>();
                d.put("participants", parts);
                d.put("names", names);
                d.put("net", parts.get(0).equals(t.borrowerUid) ? newOwed : -newOwed);
                d.put("previousNet", oldNet);
                d.put("changedBy", "TXN");
                d.put("lastTxnId", txnId);
                d.put("updatedAt", now);
                tr.set(dRef, d);
            }
            tr.update(ref, "status", accept ? Txn.ACCEPTED : Txn.DECLINED, "respondedAt", now);
            return null;
        }));
    }

    public static void cancelRequest(String txnId) throws Exception {
        await(txns().document(txnId).update("status", Txn.CANCELLED, "respondedAt", System.currentTimeMillis()));
    }

    /** Every loan event I am part of, newest first. */
    public static List<Txn> history(String me) throws Exception {
        List<Txn> list = new ArrayList<>();
        for (QueryDocumentSnapshot d : await(txns().whereArrayContains("participants", me).get())) {
            list.add(Txn.from(d));
        }
        list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return list;
    }

    // ---------------------------------------------------------------- balances

    public static List<Debt> debtsOf(String uid) throws Exception {
        List<Debt> list = new ArrayList<>();
        for (QueryDocumentSnapshot d : await(db().collection("debts").whereArrayContains("participants", uid).get())) {
            list.add(Debt.from(d));
        }
        return list;
    }

    // ---------------------------------------------------------------- circular settlement

    /**
     * Finds debt loops that pass through {@code me}: me owes A, A owes B, ..., someone owes me.
     * Depth-first search over "owes" edges, loading each person's debts only when reached.
     */
    public static List<Cycle> findCycles(String me) throws Exception {
        Map<String, List<Cycle.Edge>> outgoing = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        List<Cycle> found = new ArrayList<>();
        Set<String> onPath = new HashSet<>();
        onPath.add(me);
        dfs(me, me, new ArrayList<>(), onPath, outgoing, names, found, new HashSet<>());
        return found;
    }

    private static void dfs(String start, String node, List<Cycle.Edge> path, Set<String> onPath,
                            Map<String, List<Cycle.Edge>> outgoing, Map<String, String> names,
                            List<Cycle> found, Set<String> keys) throws Exception {
        if (found.size() >= MAX_CYCLES || outgoing.size() >= MAX_NODES) return;
        for (Cycle.Edge e : edgesFrom(node, outgoing, names)) {
            if (e.to.equals(start)) {
                if (path.size() >= 2) {
                    List<Cycle.Edge> loop = new ArrayList<>(path);
                    loop.add(e);
                    Cycle c = new Cycle(loop, names);
                    if (keys.add(c.key())) found.add(c);
                }
            } else if (!onPath.contains(e.to) && path.size() + 1 < MAX_CYCLE_LENGTH) {
                onPath.add(e.to);
                path.add(e);
                dfs(start, e.to, path, onPath, outgoing, names, found, keys);
                path.remove(path.size() - 1);
                onPath.remove(e.to);
            }
        }
    }

    private static List<Cycle.Edge> edgesFrom(String uid, Map<String, List<Cycle.Edge>> cache,
                                              Map<String, String> names) throws Exception {
        List<Cycle.Edge> edges = cache.get(uid);
        if (edges != null) return edges;
        edges = new ArrayList<>();
        for (Debt d : debtsOf(uid)) {
            names.putAll(d.names);
            long owed = d.owedBy(uid);
            if (owed > 0) edges.add(new Cycle.Edge(uid, d.other(uid), owed));
        }
        cache.put(uid, edges);
        return edges;
    }

    public static List<Proposal> openProposals(String me) throws Exception {
        List<Proposal> list = new ArrayList<>();
        for (QueryDocumentSnapshot d : await(db().collection("proposals")
                .whereArrayContains("members", me).whereEqualTo("status", Proposal.OPEN).get())) {
            list.add(Proposal.from(d));
        }
        list.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return list;
    }

    /**
     * Records my agreement. Pass {@code fresh} to start a new proposal from a detected loop.
     * If I am the last member to agree, the loop's current minimum is cancelled from every debt in
     * it, atomically, and a CYCLE entry is added to each pair's history.
     *
     * @return {@link #OUTCOME_PENDING}, {@link #OUTCOME_APPLIED} or {@link #OUTCOME_STALE}
     */
    public static String approve(String proposalId, String me, Cycle fresh) throws Exception {
        DocumentReference pRef = db().collection("proposals").document(proposalId);
        return await(db().runTransaction(tr -> {
            DocumentSnapshot snap = tr.get(pRef);
            boolean open = snap.exists() && Proposal.OPEN.equals(snap.getString("status"));
            Cycle cycle;
            List<String> approvals;
            if (open) {
                Proposal p = Proposal.from(snap);
                cycle = p.asCycle();
                approvals = p.approvals;
            } else {
                if (fresh == null) throw fail("This proposal is no longer open");
                cycle = fresh;
                approvals = new ArrayList<>();
            }
            List<String> members = cycle.members();
            if (!members.contains(me)) throw fail("You are not part of this loop");
            if (!approvals.contains(me)) approvals.add(me);
            long now = System.currentTimeMillis();

            if (!approvals.containsAll(members)) {
                if (open) tr.update(pRef, "approvals", approvals);
                else tr.set(pRef, proposalMap(cycle, approvals, me, now));
                return OUTCOME_PENDING;
            }

            // Everyone agreed. Use the debts as they are *now*, not when the loop was found.
            List<DocumentReference> refs = new ArrayList<>();
            List<Debt> debts = new ArrayList<>();
            long min = Long.MAX_VALUE;
            for (Cycle.Edge e : cycle.edges) {
                DocumentReference ref = debtRef(e.from, e.to);
                Debt d = Debt.from(tr.get(ref));
                refs.add(ref);
                debts.add(d);
                min = Math.min(min, d == null ? 0 : d.owedBy(e.from));
            }

            Map<String, Object> closing = new HashMap<>();
            closing.put("approvals", approvals);
            closing.put("closedAt", now);
            if (min <= 0) {
                closing.put("status", Proposal.STALE);
                writeProposal(tr, pRef, open, cycle, me, now, closing);
                return OUTCOME_STALE;
            }

            for (int i = 0; i < cycle.edges.size(); i++) {
                Cycle.Edge e = cycle.edges.get(i);
                Debt d = debts.get(i);
                long newOwed = d.owedBy(e.from) - min;
                long newNet = e.from.equals(d.participants.get(0)) ? newOwed : -newOwed;
                tr.update(refs.get(i), "net", newNet, "previousNet", d.net, "changedBy", "CYCLE",
                        "lastProposalId", proposalId, "updatedAt", now);
                tr.set(txns().document(), txnMap(Txn.CYCLE, e.from, cycle.names.get(e.from), e.to,
                        cycle.names.get(e.to), min, "Circular settlement", Txn.ACCEPTED, me, me, now, proposalId));
            }
            closing.put("status", Proposal.APPLIED);
            closing.put("appliedAmount", min);
            writeProposal(tr, pRef, open, cycle, me, now, closing);
            return OUTCOME_APPLIED;
        }));
    }

    public static void reject(String proposalId, String me) throws Exception {
        await(db().collection("proposals").document(proposalId).update(
                "status", Proposal.REJECTED, "rejectedBy", me, "closedAt", System.currentTimeMillis()));
    }

    private static void writeProposal(Transaction tr, DocumentReference ref, boolean exists, Cycle cycle,
                                      String me, long now, Map<String, Object> fields) {
        if (exists) {
            tr.update(ref, fields);
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> full = proposalMap(cycle, (List<String>) fields.get("approvals"), me, now);
            full.putAll(fields);
            tr.set(ref, full);
        }
    }

    private static Map<String, Object> proposalMap(Cycle cycle, List<String> approvals, String me, long now) {
        Map<String, Object> m = new HashMap<>();
        m.put("members", cycle.members());
        m.put("names", new HashMap<>(cycle.names));
        m.put("edges", cycle.edgeMaps());
        // Balance doc of each edge, in edge order; the security rules check settlements against these.
        List<String> debtIds = new ArrayList<>();
        for (Cycle.Edge e : cycle.edges) debtIds.add(debtRef(e.from, e.to).getId());
        m.put("debtIds", debtIds);
        m.put("minAmount", cycle.minAmount());
        m.put("approvals", approvals);
        m.put("status", Proposal.OPEN);
        m.put("createdBy", me);
        m.put("createdAt", now);
        return m;
    }

    private static Map<String, Object> txnMap(String type, String borrowerUid, String borrowerName,
                                              String lenderUid, String lenderName, long amount, String note,
                                              String status, String requestedBy, String respondTo, long now,
                                              String proposalId) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("borrowerUid", borrowerUid);
        m.put("borrowerName", borrowerName);
        m.put("lenderUid", lenderUid);
        m.put("lenderName", lenderName);
        m.put("amount", amount);
        m.put("note", note);
        m.put("status", status);
        m.put("requestedBy", requestedBy);
        m.put("respondTo", respondTo);
        m.put("participants", Arrays.asList(borrowerUid, lenderUid));
        m.put("createdAt", now);
        if (!Txn.PENDING.equals(status)) m.put("respondedAt", now);
        if (proposalId != null) m.put("proposalId", proposalId);
        return m;
    }
}
