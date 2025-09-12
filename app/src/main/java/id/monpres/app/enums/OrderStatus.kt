package id.monpres.app.enums

import android.content.Context
import androidx.annotation.StringRes
import id.monpres.app.R

enum class OrderStatus(
    val type: OrderStatusType,
    @param:StringRes val labelResId: Int,
    @param:StringRes val serviceActionResId: Int? = null
) {
    // Initial status when the order is created but not yet confirmed/processed
    ORDER_PLACED(OrderStatusType.OPEN, R.string.order_status_order_placed),

    // Payment has been received and order is being prepared (products) or scheduled (services)
    PROCESSING(OrderStatusType.IN_PROGRESS, R.string.order_status_processing, R.string.process),

    // Order is ready for shipment (products) or service is scheduled (services)
    ACCEPTED(OrderStatusType.IN_PROGRESS, R.string.order_status_accepted, R.string.accept),

    // For products: Order is out for delivery
    ON_THE_WAY(
        OrderStatusType.IN_PROGRESS,
        R.string.order_status_on_the_way,
        R.string.go_to_location
    ),

    // For services: Service is currently being performed
    IN_PROGRESS(
        OrderStatusType.IN_PROGRESS,
        R.string.order_status_in_progress,
        R.string.start_repairs
    ),

    // For products: Order has been shipped
    SHIPPED(OrderStatusType.IN_PROGRESS, R.string.order_status_shipped),

    // For services: Service has been completed
    REPAIRED(
        OrderStatusType.IN_PROGRESS,
        R.string.order_status_repaired,
        R.string.repairs_completed
    ),

    // For products or services: Order is awaiting payment
    WAITING_FOR_PAYMENT(
        OrderStatusType.IN_PROGRESS,
        R.string.order_status_waiting_for_payment,
        R.string.prepare_the_bills
    ),

    // Order is on hold (e.g., awaiting payment, manual review)
    ON_HOLD(OrderStatusType.IN_PROGRESS, R.string.order_status_on_hold),

    // Order has been successfully delivered (products) or completed (services)
    COMPLETED(OrderStatusType.CLOSED, R.string.order_status_completed, R.string.finish_the_order),

    // Order was canceled by the customer or business
    CANCELLED(OrderStatusType.CLOSED, R.string.order_status_cancelled),

    // Order was returned/refunded after completion
    RETURNED(OrderStatusType.CLOSED, R.string.order_status_returned),

    // Order failed due to payment issues, stock unavailability, etc.
    FAILED(OrderStatusType.CLOSED, R.string.order_status_failed),

    // For products: Shipment was delayed
    DELAYED(OrderStatusType.OPEN, R.string.order_status_delayed);

    fun getLabel(context: Context): String {
        return context.getString(this.labelResId)
    }

    fun getServiceActionLabel(context: Context): String {
        return context.getString(this.serviceActionResId ?: this.labelResId)
    }

    fun serviceNextProcess(): OrderStatus? {
        return when (this) {
            ORDER_PLACED -> ACCEPTED
            ACCEPTED -> ON_THE_WAY
            ON_THE_WAY -> IN_PROGRESS
            IN_PROGRESS -> REPAIRED
            REPAIRED -> WAITING_FOR_PAYMENT
            WAITING_FOR_PAYMENT -> COMPLETED
            COMPLETED -> COMPLETED
            CANCELLED -> CANCELLED
            else -> null
        }
    }
}

enum class OrderStatusType {
    OPEN,
    IN_PROGRESS,
    CLOSED
}