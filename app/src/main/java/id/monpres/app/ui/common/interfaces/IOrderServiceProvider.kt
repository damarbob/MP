package id.monpres.app.ui.common.interfaces

import id.monpres.app.model.OrderService

interface IOrderServiceProvider {
    fun getCurrentOrderService(): OrderService?
}
