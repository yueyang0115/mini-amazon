from django.urls import path

from . import views

urlpatterns = [
    # home page(list items)
    path('', views.home, name="home"),
    # category of home page
    path('<category>', views.home_category, name="home_category"),

    # item detail page
    path('item/<int:item_id>', views.item_detail, name="item_detail"),
    # checkout page
    path('checkout/<int:package_id>', views.checkout, name="checkout"),
    # shop_cart page
    path('shopcart', views.shop_cart, name="shop_cart"),
    # api for change cnt in shopping cart
    path('change_cnt', views.change_cnt, name="change_cnt"),

    # list_package page
    path('listpackage/', views.list_package, name='list-package'),
    # list_package_detail page
    path('listpackage/<int:package_id>/', views.list_package_detail, name = 'list-package-detail'),
]
