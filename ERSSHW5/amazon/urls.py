from django.urls import path

from . import views

urlpatterns = [
    path('', views.home, name="home"),
    # path('orderstatus/', views.order_status, name='order-status'),
    path('neworder/', views.new_order, name='new-order'),
    path('buy/', views.buy, name='buy'),
]
