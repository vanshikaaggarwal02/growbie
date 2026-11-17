package com.launchmate.growth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriorityMatrixTest {

    @Test
    void everyImpactAndEffortCombinationMapsToTheDocumentedPriority() {
        assertEquals("HIGH", PriorityMatrix.priority("HIGH", "LOW"));
        assertEquals("HIGH", PriorityMatrix.priority("HIGH", "MEDIUM"));
        assertEquals("MEDIUM", PriorityMatrix.priority("HIGH", "HIGH"));
        assertEquals("MEDIUM", PriorityMatrix.priority("MEDIUM", "LOW"));
        assertEquals("MEDIUM", PriorityMatrix.priority("MEDIUM", "MEDIUM"));
        assertEquals("LOW", PriorityMatrix.priority("MEDIUM", "HIGH"));
        assertEquals("LOW", PriorityMatrix.priority("LOW", "LOW"));
        assertEquals("LOW", PriorityMatrix.priority("LOW", "MEDIUM"));
        assertEquals("LOW", PriorityMatrix.priority("LOW", "HIGH"));
    }

    @Test
    void cheapHighImpactWorkSortsFirstAndCostlyLowImpactWorkLast() {
        record Item(String impact, String effort) {
            String priority() {
                return PriorityMatrix.priority(impact, effort);
            }
        }
        List<Item> items = new ArrayList<>();
        for (String impact : List.of("LOW", "MEDIUM", "HIGH")) {
            for (String effort : List.of("HIGH", "MEDIUM", "LOW")) {
                items.add(new Item(impact, effort));
            }
        }
        items.sort(Comparator.comparingInt(i -> PriorityMatrix.rank(i.priority(), i.impact(), i.effort())));

        assertEquals(new Item("HIGH", "LOW"), items.get(0), "high impact and low effort comes first");
        assertEquals(new Item("HIGH", "MEDIUM"), items.get(1));
        assertEquals(new Item("LOW", "HIGH"), items.get(items.size() - 1), "low impact and high effort comes last");

        // Priority never gets worse as we move down the list.
        List<String> order = List.of("HIGH", "MEDIUM", "LOW");
        for (int i = 1; i < items.size(); i++) {
            assertTrue(order.indexOf(items.get(i).priority()) >= order.indexOf(items.get(i - 1).priority()));
        }
    }

    @Test
    void withinTheSamePriorityTheCheaperEffortComesFirst() {
        // MEDIUM impact with LOW effort and MEDIUM effort are both MEDIUM priority; LOW effort wins.
        int cheap = PriorityMatrix.rank("MEDIUM", "MEDIUM", "LOW");
        int costly = PriorityMatrix.rank("MEDIUM", "MEDIUM", "MEDIUM");
        assertTrue(cheap < costly);
    }
}
