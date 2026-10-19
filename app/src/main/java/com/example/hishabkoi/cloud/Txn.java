package com.example.hishabkoi.cloud;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * One loan event between two users, stored in /txns. The borrower is always the one who owes.
 * BORROW: borrower asks lender for money; lender accepts -> debt grows.
 * REPAY:  borrower says they paid lender back; lender confirms -> debt shrinks.
 * CYCLE:  written when a circular settlement is applied; no money moves, debt shrinks.
 */
public class Txn {
    public static final String BORROW = "BORROW";
    public static final String REPAY = "REPAY";
    public static final String CYCLE = "CYCLE";

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";

    public String id;
    public String type;
    public String borrowerUid;
    public String borrowerName;
    public String lenderUid;
    public String lenderName;
    public long amount;
    public String note;
    public String status;
    public String requestedBy;
    public String respondTo;
    public String proposalId;
    public long createdAt;
    public long respondedAt;

    static Txn from(DocumentSnapshot d) {
        Txn t = new Txn();
        t.id = d.getId();
        t.type = d.getString("type");
        t.borrowerUid = d.getString("borrowerUid");
        t.borrowerName = d.getString("borrowerName");
        t.lenderUid = d.getString("lenderUid");
        t.lenderName = d.getString("lenderName");
        t.amount = Cloud.longOf(d.getLong("amount"));
        t.note = d.getString("note");
        t.status = d.getString("status");
        t.requestedBy = d.getString("requestedBy");
        t.respondTo = d.getString("respondTo");
        t.proposalId = d.getString("proposalId");
        t.createdAt = Cloud.longOf(d.getLong("createdAt"));
        t.respondedAt = Cloud.longOf(d.getLong("respondedAt"));
        return t;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public boolean iAmBorrower(String me) {
        return me.equals(borrowerUid);
    }

    public String otherName(String me) {
        return iAmBorrower(me) ? lenderName : borrowerName;
    }

    /** Cash that came into (+) or left (-) my wallet because of this loan event. */
    public long walletEffect(String me) {
        if (!ACCEPTED.equals(status)) return 0;
        long sign = iAmBorrower(me) ? 1 : -1;
        if (BORROW.equals(type)) return sign * amount;
        if (REPAY.equals(type)) return -sign * amount;
        return 0;
    }
}
