package com.narasa.imageorganiser.classifier

import org.junit.Assert.assertEquals
import org.junit.Test

// These names are stored as text in the database, so renaming one is a schema migration.
class TaxonomyTest {
    @Test
    fun primaryCategories_matchSpec() {
        assertEquals(
            listOf("DOCUMENT", "SCREENSHOT", "PHOTO", "GRAPHIC", "REVIEW"),
            PrimaryCategory.entries.map { it.name },
        )
    }

    @Test
    fun tags_matchBuildPrompt() {
        assertEquals(
            listOf("SENSITIVE", "RECEIPT", "SCHOOLWORK", "OFFICE", "EVENT_INFO", "QR", "JUNK_CANDIDATE"),
            TagName.entries.map { it.name },
        )
    }

    @Test
    fun tagStates_matchStateMachine() {
        assertEquals(
            listOf("SUGGESTED", "CONFIRMED", "REJECTED", "TRASHED"),
            TagState.entries.map { it.name },
        )
    }
}
