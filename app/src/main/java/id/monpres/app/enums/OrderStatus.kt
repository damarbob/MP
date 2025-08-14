package id.monpres.app.enums

enum class OrderStatus(val type: OrderStatusType) {
    // Initial status when the order is created but not yet confirmed/processed
    PENDING(OrderStatusType.OPEN),

    // Payment has been received and order is being prepared (products) or scheduled (services)
    PROCESSING(OrderStatusType.IN_PROGRESS),

    // Order is ready for shipment (products) or service is scheduled (services)
    CONFIRMED(OrderStatusType.IN_PROGRESS),

    // For products: Order has been shipped
    SHIPPED(OrderStatusType.IN_PROGRESS),

    // For products: Order is out for delivery
    OUT_FOR_DELIVERY(OrderStatusType.IN_PROGRESS),

    // For services: Service is currently being performed
    IN_PROGRESS(OrderStatusType.IN_PROGRESS),

    // Order has been successfully delivered (products) or completed (services)
    COMPLETED(OrderStatusType.CLOSED),

    // Order was canceled by the customer or business
    CANCELLED(OrderStatusType.CLOSED),

    // Order was returned/refunded after completion
    RETURNED(OrderStatusType.CLOSED),

    // Order failed due to payment issues, stock unavailability, etc.
    FAILED(OrderStatusType.CLOSED),

    // Order is on hold (e.g., awaiting payment, manual review)
    ON_HOLD(OrderStatusType.OPEN),

    // For products: Shipment was delayed
    DELAYED(OrderStatusType.OPEN);
}

enum class OrderStatusType {
    OPEN,
    IN_PROGRESS,
    CLOSED
}