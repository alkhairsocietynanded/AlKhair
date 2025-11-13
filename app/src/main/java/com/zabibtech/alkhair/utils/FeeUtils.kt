package com.zabibtech.alkhair.utils

object FeeUtils {

    /**
     * Calculate payment status based on base fee, discount, and paid amount.
     *
     * @param baseAmount - Total base fee
     * @param discount - Discount given
     * @param paidAmount - Amount already paid
     * @return "Paid", "Partial", or "Unpaid"
     */
    fun calculatePaymentStatus(baseAmount: Double, discount: Double, paidAmount: Double): String {
        val payable = baseAmount - discount
        return when {
            paidAmount <= 0 -> "Unpaid"
            paidAmount < payable -> "Partial"
            else -> "Paid"
        }
    }

    /**
     * Calculate remaining due amount.
     */
    fun calculateDueAmount(baseAmount: Double, discount: Double, paidAmount: Double): Double {
        val payable = baseAmount - discount
        return payable - paidAmount
    }
}
