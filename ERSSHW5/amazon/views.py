from django.shortcuts import render
from .models import *

# Create your views here.
from .utils import purchase


def home(request):
    return render(request, "amazon/home.html")


def new_order(request):
    return render(request, 'amazon/new_order.html')


def buy(request):
    if request.method == 'POST':
        apple_cnt = request.POST['apple_cnt']
        orange_cnt = request.POST['orange_cnt']
        dest_x = request.POST['x']
        dest_y = request.POST['y']

        if apple_cnt != 0 or orange_cnt != 0:
            # create a new package
            new_package = Package(dest_x=dest_x, dest_y=dest_y)
            new_package.save()

            if apple_cnt != 0:
                item = Item.objects.filter(description="apple").first()
                if item is not None:
                    product1 = Product(item=item, cnt=apple_cnt)
                    product1.save()
                    new_package.products.add(product1)
                else:
                    # TODO: maybe show some error message
                    print("no apple in DB")
                # if not item:
                # item = Item(description="apple")
                # item.save()
                # newProduct = Product(item=item, cnt=apple_cnt, package=new_package)
                # newProduct.save()

            if orange_cnt != 0:
                item = Item.objects.filter(description="orange").first()
                if not item:
                    product2 = Product(item=item, cnt=orange_cnt)
                    product2.save()
                    new_package.products.add(product2)
                else:
                    # TODO: maybe show some error message
                    print("no apple in DB")
                # item = Item(description="orange")
                # item.save()
                # newProduct = Product(item=item, cnt=orange_cnt, package=new_package)
                # newProduct.save()
            purchase(package_id=new_package.id)
    return render(request, 'amazon/success.html')
