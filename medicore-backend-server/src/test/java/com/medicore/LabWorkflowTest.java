package com.medicore;

import com.medicore.lab.LabWorkflow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for the laboratory order lifecycle (FR-LAB-04/05, AC-04). */
class LabWorkflowTest {

    @Test
    void advancesOneStepAtATime() {
        assertTrue(LabWorkflow.canAdvance("ordered", "sample_collected"));
        assertTrue(LabWorkflow.canAdvance("sample_collected", "in_progress"));
        assertTrue(LabWorkflow.canAdvance("in_progress", "result_entered"));
        assertTrue(LabWorkflow.canAdvance("result_entered", "released"));
    }

    @Test
    void neverSkipsAStep() {
        assertFalse(LabWorkflow.canAdvance("ordered", "in_progress"));
        assertFalse(LabWorkflow.canAdvance("ordered", "released"));
        assertFalse(LabWorkflow.canAdvance("sample_collected", "result_entered"));
    }

    @Test
    void neverGoesBackwards() {
        assertFalse(LabWorkflow.canAdvance("in_progress", "sample_collected"));
        assertFalse(LabWorkflow.canAdvance("result_entered", "in_progress"));
    }

    @Test
    void releasedIsTerminal() {
        assertNull(LabWorkflow.next("released"));
        assertFalse(LabWorkflow.canAdvance("released", "result_entered"));
        assertTrue(LabWorkflow.rejection("released", "in_progress").contains("already released"));
    }

    /** FR-LAB-05: the bench may work an order up to result entry, never past it. */
    @Test
    void releaseIsNotALabTechStep() {
        assertTrue(LabWorkflow.isLabTechStep("sample_collected"));
        assertTrue(LabWorkflow.isLabTechStep("in_progress"));
        assertTrue(LabWorkflow.isLabTechStep("result_entered"));
        assertFalse(LabWorkflow.isLabTechStep("released"));
    }

    @Test
    void resultsOnlyWhileTheSampleIsBeingWorked() {
        assertFalse(LabWorkflow.acceptsResults("ordered"));
        assertFalse(LabWorkflow.acceptsResults("sample_collected"));
        assertTrue(LabWorkflow.acceptsResults("in_progress"));
        assertTrue(LabWorkflow.acceptsResults("result_entered"));   // corrections before release
        assertFalse(LabWorkflow.acceptsResults("released"));
    }

    /** AC-04: a half-finished panel must never reach the patient. */
    @Test
    void releaseNeedsEveryTestAnswered() {
        assertTrue(LabWorkflow.readyForRelease("result_entered", 3, 3));
        assertFalse(LabWorkflow.readyForRelease("result_entered", 3, 2));
        assertFalse(LabWorkflow.readyForRelease("result_entered", 0, 0));
        assertFalse(LabWorkflow.readyForRelease("in_progress", 3, 3));
    }

    @Test
    void rejectionNamesTheOnlyLegalNextStep() {
        assertTrue(LabWorkflow.rejection("ordered", "released").contains("sample_collected"));
        assertTrue(LabWorkflow.rejection("bogus", "released").contains("Unknown status"));
    }
}
