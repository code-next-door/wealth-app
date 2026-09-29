package io.github.codenextdoor.wealth.grants

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.codenextdoor.wealth.data.rates.PriceUpdater
import io.github.codenextdoor.wealth.data.repository.CurrencyRepository
import kotlinx.coroutines.CoroutineScope
import io.github.codenextdoor.wealth.data.repository.ShareRepository
import io.github.codenextdoor.wealth.domain.Currency
import io.github.codenextdoor.wealth.domain.Grant
import io.github.codenextdoor.wealth.domain.Vesting
import io.github.codenextdoor.wealth.domain.formatUnits
import io.github.codenextdoor.wealth.domain.parsePositiveDecimal
import io.github.codenextdoor.wealth.ui.FormState
import io.github.codenextdoor.wealth.ui.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** The grant form's typed fields (Compose owns the text; see AccountTextFields). */
class GrantTextFields {
    val name = TextFieldState()
    val symbol = TextFieldState()
    val units = TextFieldState()
    val months = TextFieldState("48")
    val cliff = TextFieldState()
    val note = TextFieldState()
}

/** The form's choices other than text. */
data class GrantChoices(
    val currencyCode: String? = null,
    val grantDate: LocalDate = LocalDate.now(),
    val vestStart: LocalDate = LocalDate.now(),
    val intervalMonths: Int = 1,
    val showErrors: Boolean = false,
)

/** What the vesting pattern works out to, shown under the form. */
data class GrantPreview(
    val vestCount: Int,
    /** Units in each regular vest (a cliff vest is bigger). */
    val unitsPerVest: String,
    val firstVest: LocalDate,
    val lastVest: LocalDate,
    val vestedSoFar: String,
    val symbol: String,
)

data class GrantEditUiState(
    val isNew: Boolean = true,
    val isReady: Boolean = false,
    val isFinished: Boolean = false,
    val currencies: List<Currency> = emptyList(),
    val currencyCode: String = "",
    val grantDate: LocalDate = LocalDate.now(),
    val vestStart: LocalDate = LocalDate.now(),
    val intervalMonths: Int = 1,
    val nameError: Boolean = false,
    val symbolError: Boolean = false,
    val unitsError: Boolean = false,
    val monthsError: Boolean = false,
    val cliffError: Boolean = false,
    val preview: GrantPreview? = null,
)

class GrantEditViewModel(
    /** Null adds a new one. */
    private val grantId: Long?,
    private val shareRepository: ShareRepository,
    currencyRepository: CurrencyRepository,
    private val priceUpdater: PriceUpdater,
    /** Outlives the screen: the price download finishes after the form closes. */
    private val backgroundScope: CoroutineScope,
) : ViewModel() {

    /** Null when adding a new grant. */

    val fields = GrantTextFields()
    private val choices = FormState(GrantChoices())
    /** Ready once the grant (or, for a new one, its default currency) is loaded. */
    private val status = MutableStateFlow(Status(isReady = false, isFinished = false))

    internal data class Status(val isReady: Boolean, val isFinished: Boolean)

    class Data internal constructor(internal val status: Status, internal val currencies: List<Currency>?)

    val data: StateFlow<Data> = combine(status, currencyRepository.currencies) { s, c -> Data(s, c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Data(status.value, null))

    init {
        viewModelScope.launch {
            if (grantId == null) {
                // Share prices are usually quoted in dollars; else the base currency.
                val codes = currencyRepository.currencies.first().map { it.code }
                val code = if ("USD" in codes) "USD" else currencyRepository.baseCurrency.first()
                choices.update { it.copy(currencyCode = code) }
                status.update { it.copy(isReady = true) }
                return@launch
            }
            val grant = shareRepository.getGrant(grantId)
            if (grant == null) {
                status.update { it.copy(isFinished = true) }
                return@launch
            }
            fields.name.setTextAndPlaceCursorAtEnd(grant.name)
            fields.symbol.setTextAndPlaceCursorAtEnd(grant.symbol)
            fields.units.setTextAndPlaceCursorAtEnd(grant.totalUnits.stripTrailingZeros().toPlainString())
            fields.months.setTextAndPlaceCursorAtEnd(grant.vestMonths.toString())
            fields.cliff.setTextAndPlaceCursorAtEnd(if (grant.cliffMonths == 0) "" else grant.cliffMonths.toString())
            fields.note.setTextAndPlaceCursorAtEnd(grant.note.orEmpty())
            choices.value = GrantChoices(grant.currencyCode, grant.grantDate, grant.vestStart, grant.intervalMonths)
            status.update { it.copy(isReady = true) }
        }
    }

    /** The grant the form describes, or null while something is missing or invalid. */
    private fun grantOrNull(): Grant? {
        val c = choices.value
        val name = fields.name.text.toString().trim()
        val symbol = fields.symbol.text.toString().trim().uppercase()
        val units = parsePositiveDecimal(fields.units.text.toString()) ?: return null
        val months = monthsOrNull() ?: return null
        val cliff = cliffOrNull(months) ?: return null
        if (name.isEmpty() || symbol.isEmpty() || c.currencyCode == null) return null
        return Grant(
            grantId ?: 0, name, symbol, c.currencyCode, c.grantDate, units, c.vestStart, months, c.intervalMonths, cliff,
            fields.note.text.toString().trim().ifEmpty { null },
        )
    }

    private fun monthsOrNull(): Int? = fields.months.text.toString().trim().toIntOrNull()
        ?.takeIf { it in choices.value.intervalMonths..MAX_MONTHS && it % choices.value.intervalMonths == 0 }

    private fun cliffOrNull(months: Int?): Int? = fields.cliff.text.toString().trim().ifEmpty { "0" }.toIntOrNull()
        ?.takeIf { it >= 0 && (months == null || it <= months) }

    fun uiState(data: Data = this.data.value): GrantEditUiState {
        val c = choices.value
        val errors = c.showErrors
        val months = monthsOrNull()
        val preview = parsePositiveDecimal(fields.units.text.toString())?.let { units ->
            val m = months ?: return@let null
            val cliff = cliffOrNull(m) ?: return@let null
            val symbol = fields.symbol.text.toString().trim().uppercase()
            val grant = Grant(0, "", symbol, "", c.grantDate, units, c.vestStart, m, c.intervalMonths, cliff, null)
            val vests = Vesting.schedule(grant)
            val periods = m / c.intervalMonths
            GrantPreview(
                vestCount = vests.size,
                unitsPerVest = formatUnits(units.divide(BigDecimal(periods), 3, RoundingMode.DOWN)),
                firstVest = vests.first().date,
                lastVest = vests.last().date,
                vestedSoFar = formatUnits(units - Vesting.unvested(grant, LocalDate.now())),
                symbol = symbol,
            )
        }
        return GrantEditUiState(
            isNew = grantId == null,
            isReady = data.status.isReady && data.currencies != null,
            isFinished = data.status.isFinished,
            currencies = data.currencies.orEmpty(),
            currencyCode = c.currencyCode.orEmpty(),
            grantDate = c.grantDate,
            vestStart = c.vestStart,
            intervalMonths = c.intervalMonths,
            nameError = errors && fields.name.text.isBlank(),
            symbolError = errors && fields.symbol.text.isBlank(),
            unitsError = errors && parsePositiveDecimal(fields.units.text.toString()) == null,
            monthsError = errors && months == null,
            cliffError = errors && cliffOrNull(months) == null,
            preview = preview,
        )
    }

    fun onCurrencyChange(code: String) = choices.update { it.copy(currencyCode = code) }

    fun onGrantDateChange(date: LocalDate) = choices.update { it.copy(grantDate = date) }

    fun onVestStartChange(date: LocalDate) = choices.update { it.copy(vestStart = date) }

    fun onIntervalChange(months: Int) = choices.update { it.copy(intervalMonths = months) }

    fun save() {
        choices.update { it.copy(showErrors = true) }
        val grant = grantOrNull() ?: return
        viewModelScope.launch {
            shareRepository.saveGrant(grant)
            status.update { it.copy(isFinished = true) }
        }
        // So the grant's value shows straight away, not only after the next app start.
        backgroundScope.launch { priceUpdater.lookUp(grant.symbol, LocalDate.now()) }
    }

    fun delete() {
        val id = grantId ?: return
        viewModelScope.launch {
            shareRepository.deleteGrant(id)
            status.update { it.copy(isFinished = true) }
        }
    }

    companion object {
        /** Vesting longer than 50 years is surely a typo. */
        private const val MAX_MONTHS = 600

        /** How often shares can vest, in months. */
        val INTERVALS = listOf(1, 3, 6, 12)

        fun factory(grantId: Long?) = appViewModelFactory { container ->
            GrantEditViewModel(grantId, container.shareRepository, container.currencyRepository, container.priceUpdater, container.applicationScope)
        }
    }
}
