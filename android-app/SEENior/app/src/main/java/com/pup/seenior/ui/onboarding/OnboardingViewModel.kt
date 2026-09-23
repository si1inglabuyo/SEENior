package com.pup.seenior.ui.onboarding

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.pup.seenior.address.PhAddressRepository
import com.pup.seenior.address.PsgcMatch
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
import com.pup.seenior.ui.wellness.WellnessMessages

object OnboardingOptions {
    val genders = listOf("Male", "Female", "Other")
    /** Stored in `Seniors.living_arrangement`. Named because they are read outside onboarding
     *  too -- the senior dashboard decides which tabs exist from them. */
    const val LIVING_ALONE = "alone"
    const val LIVING_WITH_FAMILY = "with_family"

    val livingArrangements = listOf("With family" to LIVING_WITH_FAMILY, "Lives alone" to LIVING_ALONE)
    val yesNo = listOf("Yes", "No")

    /**
     * Stored in `Senior_Onboarding.language_preference` and read back by
     * [com.pup.seenior.ui.wellness.WellnessMessages]. Both labels are written in their own
     * language rather than both in English: a senior who reads only Filipino has to be able to
     * find their own option in this list, and "Filipino" spelled in English is no help to them.
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

    /**
     * Fills the address fields from a spot the senior pinned on the map.
     *
     * Set together and without the cascade resets the per-field setters do, because these four
     * already agree with each other — they were read out of the same PSGC entry. Running the
     * cascade would blank each level as the one above it changed.
     *
     * The barangay can legitimately arrive null: the map found the city but nothing it returned
     * matched a barangay in that city's list, and guessing is not an option for the field that
     * routes tier 3. The senior is told so on the picker and chooses from the dropdown, which by
     * then is already narrowed to the right city.
     */
    fun applyPickedAddress(match: PsgcMatch, streetLine: String) {
        region = match.regionName
        province = match.province
        city = match.city
        barangay = match.barangay
        if (streetLine.isNotBlank()) streetAddress = streetLine
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
     * **Idempotent, and that is the whole point.** [com.pup.seenior.ui.onboarding.AllSetScreen]
     * calls this from a `LaunchedEffect(Unit)`, which runs again every time that destination
     * re-enters composition -- and the permission chain immediately before it leaves and returns
     * repeatedly, once per settings page the senior is sent to. Inserting unconditionally minted
     * a fresh senior on every pass: five rows in six minutes on the realme tester handset on
     * 2026-09-18, ids 1-5, all the same person, created 14:23:04 through 14:28:58. The app then
     * followed `getOnboardedSenior()` to the newest of them and left the other four holding
     * twenty dead seed Baseline rows apiece, plus one orphan Sensor_Data row no nightly pass
     * would ever roll up or purge, because the aggregation worker only sweeps the senior the
     * app considers current.
     *
     * So an existing row is updated in place instead of duplicated, and the senior_id is held
     * stable across re-runs. That last part matters more than it looks: Sensor_Data,
     * Daily_Aggregates, Baseline and Alerts are all keyed to it, and a re-run that minted a new
     * id would orphan every reading collected up to that point.
     */
    suspend fun submitOnboarding(): Int = withContext(Dispatchers.IO) {
        val db = SeniorAppDatabase.getInstance(getApplication())
        val livingArrangementValue = OnboardingOptions.livingArrangements
            .first { it.first == livingArrangementLabel }.second
        val activityLevelValue = OnboardingOptions.activityLevels
            .first { it.first == activityLevelLabel }.second

        val resolvedId = db.withTransaction {
            // [seniorId] first, because within a single run of onboarding it names the row this
            // view model itself just wrote. The query behind it covers the case where the
            // process was killed between two passes and the view model came back empty -- which
            // is the same state a senior experiences as "it asked me everything again".
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
                // Both carried over rather than regenerated. createdAt is when this senior first
                // signed up, not when they last walked back through the form; cloudSyncId is the
                // identity the server and every paired family contact already know them by, and
                // dropping it here would strand the pairing while the phone carried on happily.
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
                // Kept from the row being replaced: both record what has already happened to
                // this senior's baseline, which is not something the questionnaire can restate.
                seedBaselineGenerated = previous?.seedBaselineGenerated ?: false,
                onboardingCompletedAt = previous?.onboardingCompletedAt ?: System.currentTimeMillis(),
                baselineReadyAt = previous?.baselineReadyAt
            )
            if (previous == null) db.seniorOnboardingDao().insert(onboarding)
            else db.seniorOnboardingDao().update(onboarding)

            // Only when there is nothing there already. Seeding unconditionally would drop a
            // senior who has lived through the fortnight back to questionnaire guesses -- the
            // section 6 hand-over run in reverse. If the declared hours really did change,
            // BaselineUpdater folds them in from the next nightly pass against real data, which
            // is the honest way to get there.
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
