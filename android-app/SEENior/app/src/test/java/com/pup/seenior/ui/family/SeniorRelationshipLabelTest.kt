package com.pup.seenior.ui.family

import com.pup.seenior.ui.wellness.WellnessMessages
import org.junit.Assert.assertEquals
import org.junit.Test

/** The family app shows the SENIOR beside the stored label, which describes the family member
 *  -- so "son" has to read as the senior being the father or mother, never "Son". */
class SeniorRelationshipLabelTest {
    private val en = FamilyStrings.forLanguage(WellnessMessages.ENGLISH)
    private val fil = FamilyStrings.forLanguage(WellnessMessages.FILIPINO)

    @Test fun sonOrDaughterMakesTheSeniorAParent() {
        assertEquals("Father", en.seniorRelationshipLabel("son", "Male"))
        assertEquals("Mother", en.seniorRelationshipLabel("daughter", "Female"))
        assertEquals("Parent", en.seniorRelationshipLabel("son", "Other"))
        assertEquals("Ama", fil.seniorRelationshipLabel("son", "Male"))
        assertEquals("Ina", fil.seniorRelationshipLabel("daughter", "Female"))
    }

    @Test fun grandchildAndSpouseAreReversed() {
        assertEquals("Grandmother", en.seniorRelationshipLabel("grandchild", "Female"))
        assertEquals("Wife", en.seniorRelationshipLabel("husband", "Female"))
        assertEquals("Husband", en.seniorRelationshipLabel("wife", "Male"))
        // Filipino has one word for both, so it is not split by gender.
        assertEquals("Asawa", fil.seniorRelationshipLabel("husband", "Female"))
        assertEquals("Asawa", fil.seniorRelationshipLabel("wife", "Male"))
    }

    @Test fun symmetricWordsStayAndUnknownOnesFallBack() {
        assertEquals("Neighbour", en.seniorRelationshipLabel("neighbour", "Male"))
        assertEquals("Family", en.seniorRelationshipLabel("niece", "Female"))
        assertEquals("Family", en.seniorRelationshipLabel(null, "Female"))
    }
}
