package io.github.codenextdoor.wealth.domain

/**
 * Which accounts make up net worth, valued with everything that defines them (history,
 * house, calculated loan, pension). Shared by the Overview and the monthly review, so
 * both show the same figures.
 */
object NetWorthAccounts {

    /**
     * [counted]: in net worth. [housesOutside]: houses and their loans while the House
     * tab's switch leaves them out. [chosenOut]: accounts the user left out (their own
     * choice wins over the houses' switch).
     */
    data class Split(
        val counted: List<ValuedAccount>,
        val housesOutside: List<ValuedAccount>,
        val chosenOut: List<ValuedAccount>,
    )

    fun split(
        accounts: List<Account>,
        entries: List<BalanceEntry>,
        types: Map<Long, AccountType>,
        decimals: Map<String, Int>,
        houses: List<Property>,
        housesCounted: Boolean,
        loans: List<Loan>,
        pensions: List<Pension>,
    ): Split {
        val loanByAccount = loans.associateBy { it.accountId }
        val pensionByAccount = pensions.associateBy { it.accountId }
        val historyByAccount = entries.groupBy { it.accountId }
        val propertyByAccount = houses.associateBy { it.accountId }
        val all = accounts.mapNotNull { account ->
            val type = types[account.accountTypeId] ?: return@mapNotNull null
            ValuedAccount(
                account = account,
                kind = type.kind,
                decimals = decimals[account.currencyCode] ?: 2,
                history = historyByAccount[account.id].orEmpty().sortedBy { it.date },
                property = propertyByAccount[account.id],
                loan = loanByAccount[account.id],
                pension = pensionByAccount[account.id],
            )
        }
        val (chosenOut, rest) = all.partition { it.account.excludedFromNetWorth }
        val houseSide = if (housesCounted) emptySet() else houses.flatMap { listOfNotNull(it.accountId, it.loanAccountId) }.toSet()
        val (outside, counted) = rest.partition { it.account.id in houseSide }
        return Split(counted, outside, chosenOut)
    }
}
