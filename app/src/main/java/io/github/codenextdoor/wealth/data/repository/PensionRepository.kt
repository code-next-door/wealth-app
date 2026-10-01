package io.github.codenextdoor.wealth.data.repository

import io.github.codenextdoor.wealth.data.db.PensionEntity
import io.github.codenextdoor.wealth.data.db.WealthDatabase
import io.github.codenextdoor.wealth.domain.Pension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

/**
 * Pensions growing with contributions: an account's yearly contributions and interest
 * rate. Its balance entries stay the known values (see domain PensionValue).
 */
class PensionRepository(private val db: WealthDatabase) {

    val pensions: Flow<List<Pension>> = db.pensionDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun forAccount(accountId: Long): Pension? = db.pensionDao().forAccount(accountId)?.toDomain()

    suspend fun save(pension: Pension) {
        val existing = db.pensionDao().forAccount(pension.accountId)
        db.pensionDao().upsert(
            PensionEntity(
                id = existing?.id ?: 0,
                accountId = pension.accountId,
                yearlyContributionMinor = pension.yearlyContributionMinor,
                yearlyRate = pension.yearlyRate.toPlainString(),
            ),
        )
    }

    /** Back to plain values: its terms go; its balance entries stay. */
    suspend fun delete(accountId: Long) = db.pensionDao().deleteForAccount(accountId)

    private fun PensionEntity.toDomain() = Pension(accountId, yearlyContributionMinor, BigDecimal(yearlyRate))
}
