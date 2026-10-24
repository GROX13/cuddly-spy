# Withdrawals are history, not deducted from the balance

The README defines a `withdrawals` table and asks GET to return each time deposit's withdrawals, but never says how withdrawals affect the balance or the interest. We treat them as historical records: they are stored and returned, and `balance` already reflects them. Neither the balance update nor the API deducts them again, and interest is calculated on `balance` alone, exactly as the frozen `updateBalance` does. Deducting withdrawals later would change balances and API results, so this is recorded here.
