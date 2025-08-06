package id.monpres.app.enums

enum class OrderStatus {
    // Initial status when the order is created but not yet confirmed/processed
    PENDING,

    // Payment has been received and order is being prepared (products) or scheduled (services)
    PROCESSING,

    // Order is ready for shipment (products) or service is scheduled (services)
    CONFIRMED,

    // For products: Order has been shipped
    SHIPPED,

    // For products: Order is out for delivery
    OUT_FOR_DELIVERY,

    // For services: Service is currently being performed
    IN_PROGRESS,

    // Order has been successfully delivered (products) or completed (services)
    COMPLETED,

    // Order was canceled by the customer or business
    CANCELLED,

    // Order was returned/refunded after completion
    RETURNED,

    // Order failed due to payment issues, stock unavailability, etc.
    FAILED,

    // Order is on hold (e.g., awaiting payment, manual review)
    ON_HOLD,

    // For products: Shipment was delayed
    DELAYED
}