from django.contrib.auth.models import User
from django.db import models
from django.utils.timezone import now

# Create your models here.

"""
README
use real amazon as reference
Item stands for the actual item we are selling.
Order stands for an order, contains an item and count.
Package stands for one pack, contains delivery-related information and list of orders.
1. Every time use click "add to cart" or "place order", we will create a new order
(NOTE: this action should happen in the item detail page)
    1.1 if user click "add to cart", we will leave the package field of order NULL
    1.2 if user click "place order", we will create a new package, and add this order into package
2. To fetch the shop car of one user ---> user.orders.filter(package__isnull=True)
3. To fetch all historical packages of one user ---> user.packages
"""


# The category of different items.
class Category(models.Model):
    category = models.CharField(max_length=50, blank=False)

    def __str__(self):
        return self.category


# This is the class which represent one specific item.
class Item(models.Model):
    description = models.CharField(max_length=100, blank=False)
    # below are some values we might want for advance feature(we can add more)
    price = models.FloatField(default=1.0)
    # TODO: maybe change to ImageField
    img = models.CharField(max_length=50, default="/static/img/sample.jpg")
    category = models.ForeignKey(Category, on_delete=models.CASCADE, default=1)

    def __str__(self):
        return self.description


# This stands for a package(stands for one purchase).
# each package can contains several products(e.g. package.products.all())
class Package(models.Model):
    # user info
    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name="packages")
    # the warehouse id where this package stores
    warehouse = models.IntegerField(default=1)
    # the status of current package, possible value:
    # processing --- purchase but not receive the successful message
    # processed  --- purchase successful
    # packing    --- package arrived warehouse and is packing
    # packed     --- package is packed
    # loading    --- the truck arrived at warehouse and is loading
    # loaded     --- finish loading
    # delivering --- delivering to destination
    # delivered  --- delivered(final state of this package)
    # error      --- any error state(should follow by the actual error message, e.g. error: illegal item)
    status = models.CharField(max_length=100, default="processing")
    dest_x = models.IntegerField(default=10)
    dest_y = models.IntegerField(default=10)
    creation_time = models.DateTimeField(default=now)

    def total(self):
        total = 0
        for order in self.orders.all():
            total += order.total()
        return total

    def __str__(self):
        return "<" + str(self.warehouse) + ", " + self.status + ">"


# product = item id + item counts
# each product should belong to one package
class Order(models.Model):
    # user info
    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name="orders")
    item = models.ForeignKey(Item, on_delete=models.SET_NULL, null=True)
    cnt = models.IntegerField(default=1)
    # package id
    package = models.ForeignKey(Package, on_delete=models.CASCADE, related_name="orders", null=True, blank=True)
    creation_time = models.DateTimeField(default=now)

    # return the total price for current order
    def total(self):
        return self.cnt * self.item.price

    def __str__(self):
        return "<" + str(self.package_id) + ", <" + str(self.item_id) + ', ' + str(self.cnt) + ">>"

