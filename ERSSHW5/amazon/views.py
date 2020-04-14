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
            new_package = Package(owner=request.user, dest_x=dest_x, dest_y=dest_y)
            new_package.save()

            if apple_cnt != 0:
                # NOTE: this may throw and NotExist exception
                apple = Item.objects.get(description="apple")
                # create will call save automatically
                new_package.orders.create(
                    owner=request.user,
                    item=apple,
                    cnt=apple_cnt
                )

            if orange_cnt != 0:
                orange = Item.objects.get(description="orange")
                # create will call save automatically
                new_package.orders.create(
                    owner=request.user,
                    item=orange,
                    cnt=orange_cnt
                )
            print("create new package: " + str(new_package.id))
            purchase(package_id=new_package.id)
    return render(request, 'amazon/success.html')
