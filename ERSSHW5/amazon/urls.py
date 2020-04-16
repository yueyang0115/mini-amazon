from django.urls import path

from . import views

urlpatterns = [
    # home page(list items)
    path('', views.home, name="home"),
    # path('orderstatus/', views.order_status, name='order-status'),
    path('neworder/', views.new_order, name='new-order'),
    path('buy/', views.buy, name='buy'),

    # item detail page
    path('item/<int:item_id>', views.item_detail, name="item_detail"),
    # checkout page
    path('checkout/<int:package_id>', views.checkout, name="checkout"),
    # shop_cart page
    path('shopcart', views.shop_cart, name="shop_cart"),
]
