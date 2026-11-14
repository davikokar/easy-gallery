package com.davide.seddio.easygallery.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.davide.seddio.easygallery.R
import com.davide.seddio.easygallery.data.AlbumRule
import com.davide.seddio.easygallery.data.AlbumRuleClause
import com.davide.seddio.easygallery.data.AlbumRuleCondition
import com.davide.seddio.easygallery.data.AlbumRuleSummary
import com.davide.seddio.easygallery.data.DatePeriod
import com.davide.seddio.easygallery.data.UnevaluatableReason
import com.davide.seddio.easygallery.data.summarizeAlbumRule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlbumInfoDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = appContext.resources.configuration.locales[0]

    private fun summarize(rule: AlbumRule?) = summarizeAlbumRule(rule) { "Italy" }

    private fun wholeYear(year: Int) = AlbumRuleClause.Time(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    private fun wholeMonth(year: Int, month: Int): AlbumRuleClause.Time {
        val first = LocalDate.of(year, month, 1)
        return AlbumRuleClause.Time(first, first.withDayOfMonth(first.lengthOfMonth()))
    }

    private fun mediumDate(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    private fun showDialog(summary: AlbumRuleSummary, onDismiss: () -> Unit = {}) {
        composeTestRule.setContent {
            AlbumInfoDialog(summary = summary, onDismiss = onDismiss)
        }
    }

    @Test
    fun dialogTitleIsAlbumInfo() {
        showDialog(AlbumRuleSummary.Manual)

        composeTestRule.onNodeWithText(appContext.getString(R.string.menu_album_info)).assertIsDisplayed()
    }

    @Test
    fun manualAlbumShowsTheManualTitleAndDescription() {
        showDialog(summarize(null))

        composeTestRule.onNodeWithTag("album_info_manual").assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.album_type_manual_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.album_type_manual_description)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.album_type_automatic_title))
            .assertCountEquals(0)
    }

    @Test
    fun automaticAlbumShowsHeadingPlaceAndDate() {
        showDialog(
            summarize(AlbumRule.Evaluatable(listOf(AlbumRuleClause.Region(country = "IT"), wholeYear(2026))))
        )

        composeTestRule.onNodeWithTag("album_info_automatic_heading").assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.album_type_automatic_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_section_place)).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_all_of, "Italy")).assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.automatic_album_section_date)).assertIsDisplayed()
        composeTestRule.onNodeWithText(yearText(2026, locale)).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.album_type_manual_title))
            .assertCountEquals(0)
    }

    @Test
    fun regionReadsAsAllOfTheRegionAndItsCountry() {
        showDialog(
            summarize(
                AlbumRule.Evaluatable(
                    listOf(AlbumRuleClause.Region(country = "IT", admin1 = "09", admin1Name = "Lombardy"))
                )
            )
        )

        composeTestRule
            .onNodeWithText(appContext.getString(R.string.automatic_album_all_of, "Lombardy, Italy"))
            .assertIsDisplayed()
    }

    @Test
    fun singlePlaceReadsAsPlaceRegionCountryWithoutAllOf() {
        showDialog(
            summarize(
                AlbumRule.Evaluatable(
                    listOf(
                        AlbumRuleClause.Region(
                            country = "IT",
                            admin1 = "09",
                            admin1Name = "Lombardy",
                            place = "Milan"
                        )
                    )
                )
            )
        )

        composeTestRule.onNodeWithText("Milan, Lombardy, Italy").assertIsDisplayed()
    }

    @Test
    fun dateOnlyAlbumHasNoPlaceEntry() {
        showDialog(summarize(AlbumRule.Evaluatable(listOf(wholeYear(2026)))))

        composeTestRule.onNodeWithTag("album_info_date").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.automatic_album_section_place))
            .assertCountEquals(0)
    }

    @Test
    fun yearsAndMonthsAreListedNewestFirst() {
        // Stored oldest first on purpose: the order on screen must come from the summary, not the rule.
        showDialog(
            summarize(
                AlbumRule.Evaluatable(
                    listOf(wholeYear(2023), wholeMonth(2024, 1), wholeMonth(2025, 12))
                )
            )
        )

        val december2025 = appContext.getString(R.string.automatic_album_month_year, monthName(12, locale), 2025)
        val january2024 = appContext.getString(R.string.automatic_album_month_year, monthName(1, locale), 2024)
        val year2023 = yearText(2023, locale)

        val tops = listOf(december2025, january2024, year2023).map { text ->
            composeTestRule.onNodeWithText(text).assertIsDisplayed()
            composeTestRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals("Dates must read newest first", tops.sorted(), tops)
        assertTrue("Dates must be on separate lines", tops.distinct().size == 3)
    }

    @Test
    fun placeAndDateKeepTheirLabelAbovePlaceFirst() {
        showDialog(
            summarize(AlbumRule.Evaluatable(listOf(wholeYear(2026), AlbumRuleClause.Region(country = "IT"))))
        )

        val placeTop = composeTestRule.onNodeWithTag("album_info_place").fetchSemanticsNode().boundsInRoot.top
        val dateTop = composeTestRule.onNodeWithTag("album_info_date").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Place must come before Date", placeTop < dateTop)

        val labelTop = composeTestRule
            .onNodeWithText(appContext.getString(R.string.automatic_album_section_place))
            .fetchSemanticsNode().boundsInRoot.top
        val valueTop = composeTestRule
            .onNodeWithText(appContext.getString(R.string.automatic_album_all_of, "Italy"))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("The label must sit above its value", labelTop < valueTop)
    }

    @Test
    fun otherDatePeriodsUseTheirOwnWording() {
        val day = LocalDate.of(2026, 3, 5)
        val from = LocalDate.of(2024, 6, 2)
        val to = LocalDate.of(2024, 8, 19)
        val since = LocalDate.of(2025, 1, 14)
        val until = LocalDate.of(2022, 11, 23)
        showDialog(
            AlbumRuleSummary.Automatic(
                listOf(
                    AlbumRuleCondition.Dates(DatePeriod.Day(day)),
                    AlbumRuleCondition.Dates(DatePeriod.Between(from, to)),
                    AlbumRuleCondition.Dates(DatePeriod.Since(since)),
                    AlbumRuleCondition.Dates(DatePeriod.Until(until))
                )
            )
        )

        listOf(
            mediumDate(day),
            appContext.getString(R.string.album_info_date_range, mediumDate(from), mediumDate(to)),
            appContext.getString(R.string.album_info_date_from, mediumDate(since)),
            appContext.getString(R.string.album_info_date_until, mediumDate(until))
        ).forEach { text ->
            composeTestRule.onNodeWithText(text).assertIsDisplayed()
        }
    }

    @Test
    fun unreadableRuleShowsTheNoticeAndNeverTheStoredText() {
        val rawRule = "RAW-RULE-TEXT-THAT-MUST-STAY-HIDDEN"
        showDialog(summarize(AlbumRule.Unevaluatable(rawRule, UnevaluatableReason.UNKNOWN_VERSION)))

        composeTestRule.onNodeWithTag("album_info_unreadable").assertIsDisplayed()
        composeTestRule.onNodeWithText(appContext.getString(R.string.album_rule_unevaluatable_notice))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText(rawRule, substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.album_type_manual_title))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(appContext.getString(R.string.album_type_automatic_title))
            .assertCountEquals(0)
    }

    @Test
    fun closeButtonDismissesTheDialog() {
        var dismissCalls = 0
        showDialog(AlbumRuleSummary.Manual, onDismiss = { dismissCalls++ })

        composeTestRule.onNodeWithText(appContext.getString(R.string.action_close)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, dismissCalls)
        }
    }
}
