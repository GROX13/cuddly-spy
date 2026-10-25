# XA Bank Time Deposits

Fixed-term savings products whose balances grow by plan-specific monthly interest.

## Language

**Time deposit**:
A customer's fixed-term savings holding with a plan type, a balance and an age in days.
_Avoid_: Account, deposit account, savings

**Plan type**:
The product a time deposit is opened under (`basic`, `student` or `premium`); it decides the interest rule.
_Avoid_: Plan, product, tier

**Days**:
How long the time deposit has existed; interest eligibility is decided from it.
_Avoid_: Age, term, duration

**Eligible days**:
The range of days in which a plan type earns monthly interest.
_Avoid_: Interest window, active period

**Balance**:
The current amount held in a time deposit.
_Avoid_: Amount, principal

**Withdrawal**:
A historical record of an amount taken out of a time deposit on a given date; the balance already reflects it.
_Avoid_: Debit, payout

**Time deposit statement**:
A time deposit's stored, exact state (id, plan type, balance to the cent, days) together with its withdrawals; it is what listing the time deposits and updating their balances show.
_Avoid_: Time deposit with withdrawals, deposit view, summary

**Monthly interest**:
One twelfth of the plan type's annual rate applied to the balance, added when balances are updated.
_Avoid_: Yield, return
