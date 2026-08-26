package com.pup.seenior.ui.onboarding

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pup.seenior.address.PhAddressRepository
import com.pup.seenior.address.RegionNode
import com.pup.seenior.baseline.SeedBaselineGenerator
import com.pup.seenior.database.SeniorAppDatabase
import com.pup.seenior.database.entities.Senior
import com.pup.seenior.database.entities.SeniorOnboarding
import com.pup.seenior.validation.PhilippinePhone
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object OnboardingOptions {
    val genders = listOf("Male", "Female", "Other")
    /** Stored in `Seniors.living_arrangement`. Named because they are read outside onboarding
     *  too -- the senior dashboard decides which tabs exist from them. */
    const val LIVING_ALONE = "alone"
    const val LIVING_WITH_FAMILY = "with_family"

    val livingArrangements = listOf("With family" to LIVING_WITH_FAMILY, "Lives alone" to LIVING_ALONE)
    val yesNo = listOf("Yes", "No")
    val napDurations = listOf("15 minutes", "30 minutes", "45 minutes", "1 hour", "1.5 hours", "2 hours or more")
    val activityLevels = listOf(
        "Mostly resting (stays in bed or chair most of the day)" to "resting",
        "Light activity (short walks around the house)" to "light",
        "Moderate activity (walks outside, light chores)" to "moderate",
        "Active (regular walking or exercise)" to "active"
    )
    val outsideTimes = listOf(
        "Morning (6 AM - 10 AM)",
        "Midday (10 AM - 2 PM)",
        "Afternoon (2 PM - 6 PM)",
        "Evening (6 PM - 9 PM)",
        "Varies (no fixed time)"
    )
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    // Sign Up ("Tell Us About Yourself")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var age by mutableStateOf("")
    var gender by mutableStateOf<String?>(null)
    var mobileNumber by mutableStateOf("")
    var livingArrangementLabel by mutableStateOf<String?>(null)

    // Structured address (delivery-style: region -> province -> city -> barangay + street line).
    // Backed by the bundled PSGC dataset; selecting a level resets everything below it.
    private var locations by mutableStateOf<Map<String, RegionNode>>(emptyMap())
    var region by mutableStateOf<String?>(null)
        private set
    var province by mutableStateOf<String?>(null)
        private set
    var city by mutableStateOf<String?>(null)
        private set
    var barangay by mutableStateOf<String?>(null)
    var streetAddress by mutableStateOf("")

    init {
        viewModelScope.launch {
            locations = PhAddressRepository.load(getApplication())
        }
    }

    private val regionNode: RegionNode?
        get() = locations.values.firstOrNull { it.regionName == region }

    val regionOptions: List<String>
        get() = locations.values.map { it.regionName }.sorted()
    val provinceOptions: List<String>
        get() = regionNode?.provinceList?.keys?.sorted() ?: emptyList()
    val cityOptions: List<String>
        get() = regionNode?.provinceList?.get(province)?.municipalityList?.keys?.sorted() ?: emptyList()
    val barangayOptions: List<String>
        get() = regionNode?.provinceList?.get(province)
            ?.municipalityList?.get(city)?.barangayList?.sorted() ?: emptyList()

    fun onRegionSelected(name: String) {
        region = name; province = null; city = null; barangay = null
    }

    fun onProvinceSelected(name: String) {
        province = name; city = null; barangay = null
    }

    fun onCitySelected(name: String) {
        city = name; barangay = null
    }

    // Onboarding questionnaire
    var wakeTime by mutableStateOf<LocalTime?>(null)
    var sleepTime by mutableStateOf<LocalTime?>(null)
    var hasNap by mutableStateOf<String?>(null)
    var napTime by mutableStateOf<LocalTime?>(null)
    var napDuration by mutableStateOf<String?>(null)
    var activityLevelLabel by mutableStateOf<String?>(null)
    var goesOutside by mutableStateOf<String?>(null)
    var outsideTime by mutableStateOf<String?>(null)
    var chargesOvernight by mutableStateOf<String?>(null)

    var seniorId: Int = 0
        private set

    val isSignUpValid: Boolean
        get() = firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            age.toIntOrNull() != null &&
            gender != null &&
            PhilippinePhone.isValid(mobileNumber) &&
            livingArrangementLabel != null &&
            region != null &&
            province != null &&
            city != null &&
            barangay != null &&
            streetAddress.isNotBlank()

    val isQuestionnaireValid: Boolean
        get() = wakeTime != null &&
            sleepTime != null &&
            hasNap != null &&
            (hasNap != "Yes" || (napTime != null && napDuration != null)) &&
            activityLevelLabel != null &&
            goesOutside != null &&
            (goesOutside != "Yes" || outsideTime != null) &&
            chargesOvernight != null

    suspend fun submitOnboarding(): Int = withContext(Dispatchers.IO) {
        val db = SeniorAppDatabase.getInstance(getApplication())
        val livingArrangementValue = OnboardingOptions.livingArrangements
            .first { it.first == livingArrangementLabel }.second
        val activityLevelValue = OnboardingOptions.activityLevels
            .first { it.first == activityLevelLabel }.second

        val insertedId = db.withTransaction {
            val senior = Senior(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                age = age.trim().toInt(),
                gender = gender!!,
                mobileNumber = PhilippinePhone.normalize(mobileNumber)!!,
                address = listOf(streetAddress.trim(), barangay!!, city!!, province!!, region!!)
                    .joinToString(", "),
                barangay = barangay!!,
                livingArrangement = livingArrangementValue,
                isOnboardingComplete = true
            )
            val id = db.seniorDao().insert(senior).toInt()

            val onboarding = SeniorOnboarding(
                seniorId = id,
                wakeTime = wakeTime!!.format(TIME_FORMAT),
                sleepTime = sleepTime!!.format(TIME_FORMAT),
                hasNap = hasNap == "Yes",
                napTime = if (hasNap == "Yes") napTime?.format(TIME_FORMAT) else null,
                napDurationMinutes = if (hasNap == "Yes") parseDurationMinutes(napDuration) else null,
                activityLevel = activityLevelValue,
                languagePreference = "en"
            )
            db.seniorOnboardingDao().insert(onboarding)

            val seedBaselines = SeedBaselineGenerator.generate(id, onboarding)
            db.baselineDao().insertAll(seedBaselines)
            db.seniorOnboardingDao().markSeedBaselineGenerated(id)

            id
        }

        seniorId = insertedId
        insertedId
    }

    private fun parseDurationMinutes(label: String?): Int? = when (label) {
        "15 minutes" -> 15
        "30 minutes" -> 30
        "45 minutes" -> 45
        "1 hour" -> 60
        "1.5 hours" -> 90
        "2 hours or more" -> 120
        else -> null
    }
}
