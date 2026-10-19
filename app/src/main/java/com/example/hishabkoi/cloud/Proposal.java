package com.example.hishabkoi.cloud;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A circular settlement waiting for every member of the loop to agree. Stored in /proposals/{cycleKey}. */
public class Proposal {
    public static final String OPEN = "OPEN";
    public static final String APPLIED = "APPLIED";
    public static final String REJECTED = "REJECTED";
    public static final String STALE = "STALE";

    public String id;
    public List<String> members = new ArrayList<>();
    public List<String> approvals = new ArrayList<>();
    public Map<String, String> names = new HashMap<>();
    public List<Cycle.Edge> edges = new ArrayList<>();
    public String status;
    public String createdBy;
    public long createdAt;

    @SuppressWarnings("unchecked")
    static Proposal from(DocumentSnapshot d) {
        Proposal p = new Proposal();
        p.id = d.getId();
        List<String> m = (List<String>) d.get("members");
        if (m != null) p.members = m;
        List<String> a = (List<String>) d.get("approvals");
        if (a != null) p.approvals = new ArrayList<>(a);
        Map<String, Object> n = (Map<String, Object>) d.get("names");
        if (n != null) for (Map.Entry<String, Object> e : n.entrySet()) p.names.put(e.getKey(), String.valueOf(e.getValue()));
        List<Map<String, Object>> raw = (List<Map<String, Object>>) d.get("edges");
        if (raw != null) {
            for (Map<String, Object> e : raw) {
                p.edges.add(new Cycle.Edge((String) e.get("from"), (String) e.get("to"),
                        ((Number) e.get("amount")).longValue()));
            }
        }
        p.status = d.getString("status");
        p.createdBy = d.getString("createdBy");
        p.createdAt = Cloud.longOf(d.getLong("createdAt"));
        return p;
    }

    public Cycle asCycle() {
        return new Cycle(edges, names);
    }

    public boolean approvedBy(String uid) {
        return approvals.contains(uid);
    }
}
