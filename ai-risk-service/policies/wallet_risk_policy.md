# Wallet Transfer Risk Policy

## Risk Levels

Low risk transfers can usually be allowed when the amount is small relative to the sender balance, the sender has normal recent activity, and no deterministic rule is triggered.

Medium risk transfers should trigger step-up verification when the amount is moderately high, the transfer uses a large share of available balance, or activity is unusual but not clearly severe.

High risk transfers should be sent to manual review when the amount is very high, the transfer drains most of the sender balance, the sender has many recent transfers, or multiple deterministic rules trigger at the same time.

## Amount Rules

Transfers at or above 1000 should be considered high amount transfers.

Transfers at or above 500 should be considered medium amount transfers.

## Balance Impact Rules

Transfers using at least 80 percent of the sender balance are high risk because they may indicate account takeover, cash-out behavior, or financial distress.

Transfers using at least 50 percent of the sender balance are medium risk and should usually require additional verification.

Transfers that would overdraw the account should not proceed.

Transfers that leave the sender with less than 100 remaining balance should be reviewed as a low-balance warning.

## Activity Rules

Many recent wallet transactions from the same user can indicate velocity risk. If the user has five or more recent transactions, the assessment should mention activity velocity as a risk factor.

## Recommended Actions

ALLOW means the deterministic risk score is low and no major policy signal was detected.

STEP_UP_VERIFICATION means the user should complete additional verification before the transfer is allowed.

MANUAL_REVIEW means the transfer should be reviewed by an operations or risk team before approval.

## AI Explanation Rules

The AI assistant must not invent account data, user data, or policy rules.

The AI assistant should cite the policy concepts that support its reasoning.

The AI assistant should treat deterministic metrics from the wallet service as source of truth.
