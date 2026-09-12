package com.snatik.storage.app.feature.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PatternUrisTest {

    @Test fun firstWildcardDetectsIdAndKey() {
        assertEquals('#', PatternUris.firstWildcard("contacts/#"))
        assertEquals('*', PatternUris.firstWildcard("contacts/lookup/*"))
        assertNull(PatternUris.firstWildcard("contacts"))
    }

    @Test fun parentPathIsSegmentsBeforeFirstWildcard() {
        assertEquals("contacts", PatternUris.parentPath("contacts/#"))
        assertEquals("contacts", PatternUris.parentPath("contacts/#/data"))
        assertEquals("contacts/lookup", PatternUris.parentPath("contacts/lookup/*"))
        // wildcard first segment → empty parent (query the authority root)
        assertEquals("", PatternUris.parentPath("#"))
        assertEquals("", PatternUris.parentPath("*/images/media/#"))
    }

    @Test fun substituteReplacesFirstWildcardOnly() {
        assertEquals("contacts/42", PatternUris.substitute("contacts/#", "42"))
        assertEquals("contacts/42/data", PatternUris.substitute("contacts/#/data", "42"))
        assertEquals("contacts/lookup/abc123", PatternUris.substitute("contacts/lookup/*", "abc123"))
        // only the first wildcard is filled; the rest remain for a later step
        assertEquals("external/images/media/#", PatternUris.substitute("*/images/media/#", "external"))
    }

    @Test fun pickIdColumnPrefersIdForHashAndKeyForStar() {
        assertEquals("_id", PatternUris.pickIdColumn(listOf("_id", "name"), '#'))
        assertEquals("lookup", PatternUris.pickIdColumn(listOf("_id", "lookup", "name"), '*'))
        // no _id → first column
        assertEquals("uuid", PatternUris.pickIdColumn(listOf("uuid", "value"), '#'))
        assertNull(PatternUris.pickIdColumn(emptyList(), '#'))
    }

    @Test fun pickLabelColumnFindsDisplayNameButNotTheIdColumn() {
        assertEquals("display_name", PatternUris.pickLabelColumn(listOf("_id", "display_name"), "_id"))
        // the id column itself is never used as its own label
        assertNull(PatternUris.pickLabelColumn(listOf("name"), "name"))
        assertNull(PatternUris.pickLabelColumn(listOf("_id", "value"), "_id"))
    }
}
