package com.epns.lambda.scenarios;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;

public class DefensiveCopyTest {
    @Test
    public void testDefensiveCopy() throws InterruptedException {
        ArrayList<Integer> list = new ArrayList<>(Collections.nCopies(10, 0));
        ScenarioRunner.runScenario("DefensiveCopy", list, l -> {
            ArrayList<Integer> copy = new ArrayList<>(l);
            copy.replaceAll(x -> x + 1);
            Collections.copy(l, copy);
        });
    }
}
