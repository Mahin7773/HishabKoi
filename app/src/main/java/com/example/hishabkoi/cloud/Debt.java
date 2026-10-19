package com.example.hishabkoi.cloud;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Running balance between two users, stored in /debts/{uidA_uidB} (uids sorted).
 * {@code net > 0} means participants[0] owes participants[1]; negative means the reverse.
 */
public class Debt {
    public String id;
    public List<String> participants;
    public Map<String, String> names = new HashMap<>();
    public long net;
    public Long previousNet;
    /** "TXN" or "CYCLE" — what caused the last change. */
    public String changedBy;
    public long updatedAt;

    @SuppressWarnings("unchecked")
    static Debt from(DocumentSnapshot d) {
        if (!d.exists()) return null;
        Debt debt = new Debt();
        debt.id = d.getId();
        debt.participants = (List<String>) d.get("participants");
        Map<String, Object> raw = (Map<String, Object>) d.get("names");
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) debt.names.put(e.getKey(), String.valueOf(e.getValue()));
        }
        debt.net = Cloud.longOf(d.getLong("net"));
        debt.previousNet = d.getLong("previousNet");
        debt.changedBy = d.getString("changedBy");
        debt.updatedAt = Cloud.longOf(d.getLong("updatedAt"));
        return debt;
    }

    /** How much {@code uid} owes the other person (negative = the other person owes them). */
    public long owedBy(String uid) {
        return uid.equals(participants.get(0)) ? net : -net;
    }

    public Long previousOwedBy(String uid) {
        if (previousNet == null) return null;
        return uid.equals(participants.get(0)) ? previousNet : -previousNet;
    }

    public String other(String uid) {
        return uid.equals(participants.get(0)) ? participants.get(1) : participants.get(0);
    }

    public String name(String uid) {
        String n = names.get(uid);
        return n != null ? n : "?";
    }

    public boolean changedByCycle() {
        return "CYCLE".equals(changedBy) && previousNet != null && previousNet != net;
    }
}
