package com.pup.seenior.ui.onboarding

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pup.seenior.address.AddressForm
import com.pup.seenior.address.PsgcMatch
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
import com.pup.seenior.ui.wellness.WellnessMessages

object OnboardingOptions {
    val genders = listOf("Male", "Female", "Other")
    /** Stored in `Seniors.living_arrangement`. Named because the senior dashboard reads them to choose its tabs. */
    const val LIVING_ALONE = "alone"
    const val LIVING_WITH_FAMILY = "with_family"

    val livingArrangements = listOf("With family" to LIVING_WITH_FAMILY, "Lives alone" to LIVING_ALONE)
    val yesNo = listOf("Yes", "No")

    /**
     * Stored in `Senior_Onboarding.language_preference` and read by
     * [com.pup.seenior.ui.wellness.WellnessMessages]. Each label is written in its own language
     * so a senior who reads only Filipino can find their option.
     */
    val languages = listOf(
        "English" to WellnessMessages.ENGLISH,
        "Filipino / Tagalog" to WellnessMessages.FILIPINO,
    )
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

    // Structured address (region -> province -> city -> barangay + street), held in
    // [AddressForm] so Edit Profile accepts one the same way. These members just forward to it.
    val addressForm = AddressForm()
    val region get() = addressForm.region
    val province get() = addressForm.province
    val city get() = addressForm.city
    var barangay: String?
        get() = addressForm.barangay
        set(value) { value?.let(addressForm::onBarangaySelected) }
    var streetAddress: String
        get() = addressForm.streetAddress
        set(value) = addressForm.onStreetChanged(value)

    init {
        viewModelScope.launch { addressForm.load(getApplication()) }
    }

    val regionOptions get() = addressForm.regionOptions
    val provinceOptions get() = addressForm.provinceOptions
    val cityOptions get() = addressForm.cityOptions
    val barangayOptions get() = addressForm.barangayOptions

    fun onRegionSelected(name: String) = addressForm.onRegionSelected(name)
    fun onProvinceSelected(name: String) = addressForm.onProvinceSelected(name)
    fun onCitySelected(name: String) = addressForm.onCitySelected(name)
    fun applyPickedAddress(match: PsgcMatch, streetLine: String) =
        addressForm.applyPickedAddress(match, streetLine)

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
    var languageLabel by mutableStateOf<String?>(null)

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
            chargesOvernight != null &&
            languageLabel != null

    /**
     * Writes the senior, their questionnaire answers and their seed Baseline, and returns the
     * senior_id this install will use from now on.
     *
     * Idempotent. [com.pup.seenior.ui.onboarding.AllSetScreen] calls this from a
     * `LaunchedEffect(Unit)` that runs every time the destination re-enters composition, and
     * the permission chain before it leaves and returns repeatedly. Inserting each time
     * created five senior rows in six minutes on a tester handset, leaving dead seed Baseline
     * rows and an orphan Sensor_Data row. So an existing row is updated in place and the
     * senior_id stays stable; Sensor_Data, Daily_Aggregates, Baseline and Alerts are all keyed
     * to it.
     */
    suspend fun submitOnboarding(): Int = withContext(Dispatchers.IO) {
        val db = SeniorAppDatabase.getInstance(getApplication())
        val livingArrangementValue = OnboardingOptions.livingArrangements
            .first { it.first == livingArrangementLabel }.second
        val activityLevelValue = OnboardingOptions.activityLevels
            .first { it.first == activityLevelLabel }.second

        val resolvedId = db.withTransaction {
            // [seniorId] first, since it names the row this view model just wrote. The query
            // covers the process being killed between passes and the view model coming back empty.
            val existing = db.seniorDao().getById(seniorId) ?: db.seniorDao().getOnboardedSenior()

            val senior = Senior(
                seniorId = existing?.seniorId ?: 0,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                age = age.trim().toInt(),
                gender = gender!!,
                mobileNumber = PhilippinePhone.normalize(mobileNumber)!!,
                address = listOf(streetAddress.trim(), barangay!!, city!!, province!!, region!!)
                    .joinToString(", "),
                barangay = barangay!!,
                livingArrangement = livingArrangementValue,
                // Carried over, not regenerated: createdAt is first sign-up, and cloudSyncId is
                // the identity the server and paired family know, which the pairing would lose.
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                isOnboardingComplete = true,
                cloudSyncId = existing?.cloudSyncId
            )

            val id = if (existing == null) {
                db.seniorDao().insert(senior).toInt()
            } else {
                db.seniorDao().update(senior)
                existing.seniorId
            }

            val previous = db.seniorOnboardingDao().getBySeniorId(id)
            val onboarding = SeniorOnboarding(
                onboardingId = previous?.onboardingId ?: 0,
                seniorId = id,
                wakeTime = wakeTime!!.format(TIME_FORMAT),
                sleepTime = sleepTime!!.format(TIME_FORMAT),
                hasNap = hasNap == "Yes",
                napTime = if (hasNap == "Yes") napTime?.format(TIME_FORMAT) else null,
                napDurationMinutes = if (hasNap == "Yes") parseDurationMinutes(napDuration) else null,
                activityLevel = activityLevelValue,
                languagePreference = OnboardingOptions.languages
                    .first { it.first == languageLabel }.second,
                // Kept from the row being replaced: they record what already happened to the baseline.
                seedBaselineGenerated = previous?.seedBaselineGenerated ?: false,
                onboardingCompletedAt = previous?.onboardingCompletedAt ?: System.currentTimeMillis(),
                baselineReadyAt = previous?.baselineReadyAt
            )
            if (previous == null) db.seniorOnboardingDao().insert(onboarding)
            else db.seniorOnboardingDao().update(onboarding)

            // Only when nothing is there. Seeding again would drop a senior who has lived through
            // the fortnight back to questionnaire guesses. If the declared hours changed,
            // BaselineUpdater folds that in from real data.
            if (db.baselineDao().getAllBySeniorOnce(id).isEmpty()) {
                db.baselineDao().insertAll(SeedBaselineGenerator.generate(id, onboarding))
                db.seniorOnboardingDao().markSeedBaselineGenerated(id)
            }

            id
        }

        seniorId = resolvedId
        resolvedId
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
