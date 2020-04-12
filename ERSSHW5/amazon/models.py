from django.db import models

# Create your models here.


# This is the class which represent one specific item.
class Item(models.Model):
    description = models.CharField(max_length=100, blank=False)

    def __str__(self):
        return self.description


# This stands for a package(stands for one purchase).
# each package can contains several products(e.g. package.products.all())
class Package(models.Model):
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

    def __str__(self):
        return "<" + str(self.warehouse) + ", " + self.status + ">"


# product = item + item counts
class Product(models.Model):
    item = models.OneToOneField(Item, on_delete=models.SET_DEFAULT, default="1", related_name="item")
    cnt = models.IntegerField(default=1)
    # the package this product belong to
    package = models.ForeignKey(Package, on_delete=models.CASCADE, related_name="products")

    def __str__(self):
        return "<" + str(self.package_id) + " " + str(self.item) + ', ' + str(self.cnt) + ">"

