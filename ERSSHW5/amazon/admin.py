from django.contrib import admin
from .models import Package, Product, Item
admin.site.register(Item)
admin.site.register(Package)
admin.site.register(Product)
# Register your models here.
