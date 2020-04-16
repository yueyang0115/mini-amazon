from django.urls import path

from . import views

urlpatterns = [
    path('', views.home, name="home"),
    # path('orderstatus/', views.order_status, name='order-status'),
    path('neworder/', views.new_order, name='new-order'),
    path('buy/', views.buy, name='buy'),
    path('item/<int:item_id>', views.item_detail, name='item_detail'),
    path('checkout/<int:package_id>', views.checkout, name='checkout')
]
