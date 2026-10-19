// Firestore rules tests for HishabKoi. Run inside the emulator:
//   npx firebase emulators:exec --only firestore --project demo-hishab "node rules.test.js"
const fs = require('fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');

const RULES = process.argv[2] || require('path').join(__dirname, '..', 'firestore.rules');
let env, pass = 0, fail = 0;

async function check(name, expectOk, fn) {
  try {
    if (expectOk) await assertSucceeds(fn()); else await assertFails(fn());
    pass++; console.log('  ok   ' + name);
  } catch (e) {
    fail++; console.log('  FAIL ' + name + '  -> ' + (e && e.message ? e.message.split('\n')[0] : e));
  }
}
const as = (uid) => env.authenticatedContext(uid, { email_verified: true }).firestore();
const pair = (x, y) => (x < y ? x + '_' + y : y + '_' + x);

// Same shapes the app writes (Cloud.java).
function txn(type, borrower, lender, amount, extra = {}) {
  return Object.assign({
    type, borrowerUid: borrower, borrowerName: borrower, lenderUid: lender, lenderName: lender,
    amount, note: '', status: 'PENDING', requestedBy: borrower, respondTo: lender,
    participants: [borrower, lender], createdAt: 1,
  }, extra);
}
function debtDoc(x, y, xOwes) { // xOwes > 0 means x owes y
  const parts = [x, y].sort();
  return { participants: parts, names: { [x]: x, [y]: y }, net: parts[0] === x ? xOwes : -xOwes,
           changedBy: 'TXN', updatedAt: 1 };
}
function proposal(edges, me, createdAt) {
  return { members: edges.map(e => e.from), names: {}, edges, debtIds: edges.map(e => pair(e.from, e.to)), minAmount: Math.min(...edges.map(e => e.amount)),
           approvals: [me], status: 'OPEN', createdBy: me, createdAt };
}

// The app's accept: transaction that sets the pair balance and marks the request accepted.
async function accept(db, tid, me, netOverride) {
  return db.runTransaction(async (tr) => {
    const tRef = db.collection('txns').doc(tid);
    const t = (await tr.get(tRef)).data();
    const dRef = db.collection('debts').doc(pair(t.borrowerUid, t.lenderUid));
    const d = await tr.get(dRef);
    const parts = [t.borrowerUid, t.lenderUid].sort();
    const oldNet = d.exists ? d.data().net : 0;
    const owed = parts[0] === t.borrowerUid ? oldNet : -oldNet;
    const newOwed = owed + (t.type === 'BORROW' ? t.amount : -t.amount);
    const net = netOverride !== undefined ? netOverride : (parts[0] === t.borrowerUid ? newOwed : -newOwed);
    tr.set(dRef, { participants: parts, names: {}, net, previousNet: oldNet, changedBy: 'TXN',
                   lastTxnId: tid, updatedAt: 5 });
    tr.update(tRef, { status: 'ACCEPTED', respondedAt: 5 });
  });
}

// The app's final approval: cancel `amount` from every edge (or a tampered variant).
async function apply(db, pid, me, opts = {}) {
  const now = opts.now || 1000;
  return db.runTransaction(async (tr) => {
    const pRef = db.collection('proposals').doc(pid);
    const p = (await tr.get(pRef)).data();
    const edges = opts.edges || p.edges;
    const reads = [];
    for (const e of edges) reads.push(await tr.get(db.collection('debts').doc(pair(e.from, e.to))));
    let min = Infinity;
    reads.forEach((s, i) => {
      const d = s.data(); const e = edges[i];
      min = Math.min(min, d.participants[0] === e.from ? d.net : -d.net);
    });
    const amount = opts.amount || min;
    edges.forEach((e, i) => {
      if (opts.skip === i) return;
      const d = reads[i].data();
      const owed = (d.participants[0] === e.from ? d.net : -d.net) - amount + (opts.tweak === i ? 1 : 0);
      tr.update(reads[i].ref, { net: d.participants[0] === e.from ? owed : -owed, previousNet: d.net,
                                changedBy: 'CYCLE', lastProposalId: pid, updatedAt: now });
      if (!opts.noAudit) {
        tr.set(db.collection('txns').doc(), txn('CYCLE', e.from, e.to, amount,
          { status: 'ACCEPTED', requestedBy: me, respondTo: me, respondedAt: now, proposalId: pid, note: 'Circular settlement' }));
      }
    });
    tr.update(pRef, { approvals: p.approvals.concat(p.approvals.includes(me) ? [] : [me]), closedAt: now,
                      status: 'APPLIED', appliedAmount: amount });
  });
}

async function read(path) {
  let out;
  await env.withSecurityRulesDisabled(async (ctx) => { out = (await ctx.firestore().doc(path).get()).data(); });
  return out;
}
async function seed(data) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [path, doc] of Object.entries(data)) await db.doc(path).set(doc);
  });
}
const loop3 = [{ from: 'a', to: 'b', amount: 100 }, { from: 'b', to: 'c', amount: 50 }, { from: 'c', to: 'a', amount: 70 }];
const loopDebts = {
  ['debts/a_b']: debtDoc('a', 'b', 100), ['debts/b_c']: debtDoc('b', 'c', 50), ['debts/a_c']: debtDoc('c', 'a', 70),
};

(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-hishab',
    firestore: { rules: fs.readFileSync(RULES, 'utf8'), host: '127.0.0.1', port: 8085 },
  });

  console.log('Users / usernames');
  await seed({ 'usernames/bob': { uid: 'b' } });
  const dbSign = as('a');
  await check('sign-up claims own username', true, () => dbSign.runTransaction(async (tr) => {
    tr.set(dbSign.doc('usernames/alice'), { uid: 'a' });
    tr.set(dbSign.doc('users/a'), { username: 'alice', email: 'a@x', createdAt: 1 });
  }));
  await check('profile cannot claim someone else\'s username', false,
    () => as('c').doc('users/c').set({ username: 'bob', email: 'c@x', createdAt: 1 }));
  await check('username cannot be changed later', false,
    () => as('a').doc('users/a').update({ username: 'bob' }));

  console.log('Borrow / repay requests');
  await check('borrower sends a request to the lender', true,
    () => as('b').collection('txns').doc('t1').set(txn('BORROW', 'b', 'c', 40)));
  await check('request answerable by the borrower themself', false,
    () => as('b').collection('txns').doc('t2').set(txn('REPAY', 'b', 'c', 40, { respondTo: 'b' })));
  await check('request hidden from the lender', false,
    () => as('b').collection('txns').doc('t3').set(txn('BORROW', 'b', 'c', 40, { participants: ['b'] })));
  await check('borrower cannot accept their own request', false, () => accept(as('b'), 't1', 'b'));
  await check('lender accepting with a made-up balance', false, () => accept(as('c'), 't1', 'c', 999));
  await check('lender accepts: balance moves by exactly the amount', true, () => accept(as('c'), 't1', 'c'));
  await check('repay request', true,
    () => as('b').collection('txns').doc('t4').set(txn('REPAY', 'b', 'c', 15)));
  await check('lender confirms repayment', true, () => accept(as('c'), 't4', 'c'));
  const bc = await read('debts/b_c');
  await check('balance after borrow 40, repay 15 is 25', true, async () => { if (bc.net !== 25) throw new Error('net ' + bc.net); });
  await check('third party cannot rewrite a balance', false,
    () => as('d').doc('debts/b_c').update({ net: 0, previousNet: 25, changedBy: 'TXN', lastTxnId: 't4', updatedAt: 9 }));

  console.log('Circular settlement: proposals');
  await env.clearFirestore();
  await seed(loopDebts);
  await check('creating a proposal already APPLIED', false, () =>
    as('a').doc('proposals/k').set(Object.assign(proposal(loop3, 'a', 10), { status: 'APPLIED', appliedAmount: 50 })));
  await check('creating a proposal with someone else\'s approval', false, () =>
    as('a').doc('proposals/k').set(Object.assign(proposal(loop3, 'a', 10), { approvals: ['a', 'b'] })));
  await check('debt ids that do not match the edges', false, () =>
    as('a').doc('proposals/k').set(Object.assign(proposal(loop3, 'a', 10), { debtIds: ['a_b', 'b_c', 'c_d'] })));
  await check('creating a broken loop (edges don\'t connect)', false, () =>
    as('a').doc('proposals/k').set(proposal([loop3[0], loop3[2], loop3[1]], 'a', 10)));
  await check('outsider proposing a loop they are not in', false, () =>
    as('d').doc('proposals/k').set(Object.assign(proposal(loop3, 'a', 10), { createdBy: 'd', approvals: ['d'] })));
  await check('member proposes the loop', true, () => as('a').doc('proposals/k').set(proposal(loop3, 'a', 10)));
  await check('adding another member\'s approval', false,
    () => as('a').doc('proposals/k').update({ approvals: ['a', 'c'] }));
  await check('changing the loop after proposing', false,
    () => as('a').doc('proposals/k').update({ edges: loop3.map(e => Object.assign({}, e, { amount: 1 })) }));
  await check('member agrees (own approval only)', true,
    () => as('b').doc('proposals/k').update({ approvals: ['a', 'b'] }));
  await check('marking APPLIED without changing any debts', false,
    () => as('c').doc('proposals/k').update({ approvals: ['a', 'b', 'c'], status: 'APPLIED', appliedAmount: 50, closedAt: 1000 }));
  await check('settling only 2 of the 3 debts', false, () => apply(as('c'), 'k', 'c', { skip: 1 }));
  await check('one debt dropped by the wrong amount', false, () => apply(as('c'), 'k', 'c', { tweak: 0 }));
  await check('cancelling more than the smallest debt (flips a debt)', false, () => apply(as('c'), 'k', 'c', { amount: 60 }));
  await check('a debt outside the loop sneaked into the settlement', false, async () => {
    await seed({ 'debts/c_d': debtDoc('c', 'd', 30) });
    const db = as('c');
    return db.runTransaction(async (tr) => {
      const d = (await tr.get(db.doc('debts/c_d'))).data();
      await tr.get(db.doc('proposals/k'));
      tr.update(db.doc('debts/c_d'), { net: 0, previousNet: d.net, changedBy: 'CYCLE', lastProposalId: 'k', updatedAt: 1000 });
      tr.update(db.doc('proposals/k'), { approvals: ['a', 'b', 'c'], status: 'APPLIED', appliedAmount: 30, closedAt: 1000 });
    });
  });
  await check('outsider cannot agree', false, () => as('d').doc('proposals/k').update({ approvals: ['a', 'b', 'd'] }));
  await check('last member agrees: whole loop settled at once', true, () => apply(as('c'), 'k', 'c'));
  const after = [];
  for (const id of ['a_b', 'b_c', 'a_c']) after.push((await read('debts/' + id)).net);
  await check('debts after settling are 50, 0, 20', true, async () => {
    if (JSON.stringify(after) !== JSON.stringify([50, 0, -20])) throw new Error(JSON.stringify(after));
  });
  const dbA = as('a');
  await check('replaying the same settlement again', false, () => dbA.runTransaction(async (tr) => {
    const db = dbA;
    const d = (await tr.get(db.doc('debts/a_b'))).data();
    tr.update(db.doc('debts/a_b'), { net: d.net - 50, previousNet: d.net, changedBy: 'CYCLE', lastProposalId: 'k', updatedAt: 1000 });
  }));
  await check('fake CYCLE history entry for an applied proposal', false, () =>
    as('a').collection('txns').doc().set(txn('CYCLE', 'a', 'b', 50, { status: 'ACCEPTED', proposalId: 'k' })));

  console.log('Circular settlement: reject, stale, re-open');
  await check('re-opening with a start time before it closed', false,
    () => as('a').doc('proposals/k').set(proposal(loop3, 'a', 500)));
  await check('member re-opens the loop later', true, () => as('a').doc('proposals/k').set(proposal(loop3, 'a', 2000)));
  await check('outsider cannot reject', false,
    () => as('d').doc('proposals/k').update({ status: 'REJECTED', rejectedBy: 'd', closedAt: 2100 }));
  await check('rejecting in someone else\'s name', false,
    () => as('b').doc('proposals/k').update({ status: 'REJECTED', rejectedBy: 'a', closedAt: 2100 }));
  await check('member rejects', true,
    () => as('b').doc('proposals/k').update({ status: 'REJECTED', rejectedBy: 'b', closedAt: 2100 }));
  await check('re-opened again after rejection', true, () => as('c').doc('proposals/k').set(proposal(loop3, 'c', 3000)));
  await as('a').doc('proposals/k').update({ approvals: ['c', 'a'] });
  await check('last member closes it as STALE (a debt is already 0)', true,
    () => as('b').doc('proposals/k').update({ approvals: ['c', 'a', 'b'], status: 'STALE', closedAt: 3100 }));

  // A loop of n people; everyone but the last agrees, and the last one applies it.
  async function bigLoop(people) {
    await env.clearFirestore();
    const n = people.length;
    const edges = people.map((u, i) => ({ from: u, to: people[(i + 1) % n], amount: 10 * (i + 3) }));
    const debts = {};
    edges.forEach(e => { debts['debts/' + pair(e.from, e.to)] = debtDoc(e.from, e.to, e.amount); });
    await seed(debts);
    return edges;
  }
  async function agreeAll(pid, people) {
    for (const u of people) {
      const cur = (await read('proposals/' + pid)).approvals;
      await as(u).doc('proposals/' + pid).update({ approvals: cur.concat([u]) });
    }
  }

  console.log('Circular settlement: 6-person loop');
  const six = ['a', 'b', 'c', 'd', 'e', 'f'];
  const loop6 = await bigLoop(six);
  await check('6-person loop proposed', true, () => as('a').doc('proposals/k6').set(proposal(loop6, 'a', 10)));
  await agreeAll('k6', six.slice(1, -1));
  await check('6-person loop applied', true, () => apply(as('f'), 'k6', 'f'));

  console.log('Circular settlement: 8-person loop (largest the app searches)');
  const eight = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
  const loop8 = await bigLoop(eight);
  await check('8-person loop proposed', true, () => as('a').doc('proposals/k8').set(proposal(loop8, 'a', 10)));
  await agreeAll('k8', eight.slice(1, -1));
  await check('8-person loop: skipping the last debt is refused', false, () => apply(as('h'), 'k8', 'h', { skip: 7 }));
  await check('8-person loop applied (stays within read limits)', true, () => apply(as('h'), 'k8', 'h'));
  const after8 = [];
  for (const e of loop8) {
    const d = await read('debts/' + pair(e.from, e.to));
    after8.push(d.participants[0] === e.from ? d.net : -d.net);
  }
  await check('every one of the 8 debts dropped by the smallest (30)', true, async () => {
    const want = loop8.map(e => e.amount - 30);
    if (JSON.stringify(after8) !== JSON.stringify(want)) throw new Error(JSON.stringify(after8));
  });

  console.log('Circular settlement: 9 people is over the limit');
  const nine = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'];
  const loop9 = await bigLoop(nine);
  await check('9-person loop cannot be proposed', false, () => as('a').doc('proposals/k9').set(proposal(loop9, 'a', 10)));

  await env.cleanup();
  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})().catch(async (e) => { console.error(e); process.exit(1); });
