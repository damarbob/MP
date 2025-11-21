package id.monpres.app.interfaces // Use your actual package

import id.monpres.app.model.OrderService

interface IOrderServiceProvider {
    fun getCurrentOrderService(): OrderService?
}