package com.example.hishabkoi.cloud;

import com.example.hishabkoi.util.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A closed loop of debts: edges[0].from owes edges[0].to, edges[0].to == edges[1].from, ...,
 * and the last edge points back to edges[0].from.
 */
public class Cycle {
    public static class Edge {
        public final String from;
        public final String to;
        public final long amount;

        public Edge(String from, String to, long amount) {
            this.from = from;
            this.to = to;
            this.amount = amount;
        }
    }

    public final List<Edge> edges;
    public final Map<String, String> names;

    public Cycle(List<Edge> edges, Map<String, String> names) {
        this.edges = new ArrayList<>(edges);
        this.names = new HashMap<>(names);
    }

    public List<String> members() {
        List<String> m = new ArrayList<>();
        for (Edge e : edges) m.add(e.from);
        return m;
    }

    public long minAmount() {
        long min = Long.MAX_VALUE;
        for (Edge e : edges) min = Math.min(min, e.amount);
        return min;
    }

    /** Same loop always gives the same key no matter which member found it. Used as the proposal id. */
    public String key() {
        List<String> m = members();
        int start = m.indexOf(Collections.min(m));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < m.size(); i++) {
            if (i > 0) sb.append('-');
            sb.append(m.get((start + i) % m.size()));
        }
        return sb.toString();
    }

    public String name(String uid) {
        String n = names.get(uid);
        return "@" + (n != null ? n : "?");
    }

    /** "@x → @y → @z → @x" */
    public String path() {
        StringBuilder sb = new StringBuilder();
        for (Edge e : edges) sb.append(name(e.from)).append(" → ");
        return sb.append(name(edges.get(0).from)).toString();
    }

    /** "With @x and @y": the other members, in loop order, without saying who owes whom. */
    public String withOthers(String me) {
        return "With " + others(me);
    }

    /** "@x, @y and @z": everyone in the loop except me. */
    public String others(String me) {
        List<String> others = new ArrayList<>();
        for (String m : members()) if (!m.equals(me)) others.add(name(m));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < others.size(); i++) {
            if (i > 0) sb.append(i == others.size() - 1 ? " and " : ", ");
            sb.append(others.get(i));
        }
        return sb.toString();
    }

    /**
     * Only {@code me}'s own debts in the loop, before and after cancelling the minimum. Debts between
     * other members stay private; the app shows a person only what they owe and are owed.
     */
    public String describeFor(String me) {
        long min = minAmount();
        StringBuilder sb = new StringBuilder();
        for (Edge e : edges) {
            String who;
            if (e.from.equals(me)) who = "You owe " + name(e.to);
            else if (e.to.equals(me)) who = name(e.from) + " owes you";
            else continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(who).append(": ").append(Money.format(e.amount)).append(" → ").append(Money.format(e.amount - min));
        }
        return sb.toString();
    }

    /** One line per debt, showing the amount before and after cancelling the loop's minimum. */
    public String describe() {
        long min = minAmount();
        StringBuilder sb = new StringBuilder();
        for (Edge e : edges) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(name(e.from)).append(" owes ").append(name(e.to)).append(": ")
                    .append(Money.format(e.amount)).append(" → ").append(Money.format(e.amount - min));
        }
        return sb.toString();
    }

    List<Map<String, Object>> edgeMaps() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Edge e : edges) {
            Map<String, Object> m = new HashMap<>();
            m.put("from", e.from);
            m.put("to", e.to);
            m.put("amount", e.amount);
            list.add(m);
        }
        return list;
    }
}
