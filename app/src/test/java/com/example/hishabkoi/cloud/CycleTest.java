package com.example.hishabkoi.cloud;

import static org.junit.Assert.assertEquals;

import com.example.hishabkoi.util.Money;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CycleTest {
    private static Map<String, String> names() {
        Map<String, String> n = new HashMap<>();
        n.put("x", "x");
        n.put("y", "y");
        n.put("z", "z");
        return n;
    }

    /** x owes y 100, y owes z 50, z owes x 70 -> cancel 50 -> 50, 0, 20. */
    @Test
    public void exampleFromSpec() {
        Cycle c = new Cycle(Arrays.asList(
                new Cycle.Edge("x", "y", 10000),
                new Cycle.Edge("y", "z", 5000),
                new Cycle.Edge("z", "x", 7000)), names());
        assertEquals(5000, c.minAmount());
        assertEquals("@x owes @y: ৳100 → ৳50\n@y owes @z: ৳50 → ৳0\n@z owes @x: ৳70 → ৳20", c.describe());
        assertEquals("@x → @y → @z → @x", c.path());
    }

    /** Each member sees only their own two debts in the loop, and the others without arrows. */
    @Test
    public void memberSeesOnlyOwnDebts() {
        Cycle c = new Cycle(Arrays.asList(
                new Cycle.Edge("x", "y", 10000),
                new Cycle.Edge("y", "z", 5000),
                new Cycle.Edge("z", "x", 7000)), names());
        assertEquals("You owe @y: ৳100 → ৳50\n@z owes you: ৳70 → ৳20", c.describeFor("x"));
        assertEquals("With @y and @z", c.withOthers("x"));
        assertEquals("@x owes you: ৳100 → ৳50\nYou owe @z: ৳50 → ৳0", c.describeFor("y"));
    }

    @Test
    public void keyIsSameFromEveryStartingMember() {
        Cycle fromX = new Cycle(Arrays.asList(
                new Cycle.Edge("x", "y", 1), new Cycle.Edge("y", "z", 1), new Cycle.Edge("z", "x", 1)), names());
        Cycle fromZ = new Cycle(Arrays.asList(
                new Cycle.Edge("z", "x", 1), new Cycle.Edge("x", "y", 1), new Cycle.Edge("y", "z", 1)), names());
        assertEquals(fromX.key(), fromZ.key());
        assertEquals("x-y-z", fromZ.key());
    }

    @Test
    public void moneyParsing() {
        assertEquals(12050, Money.parse("120.5"));
        assertEquals(-1, Money.parse("1.234"));
        assertEquals(-1, Money.parse("0"));
        assertEquals(-1, Money.parse("abc"));
        assertEquals("৳1,234.50", Money.format(123450));
        assertEquals("120.5", Money.plain(12050));
    }
}
