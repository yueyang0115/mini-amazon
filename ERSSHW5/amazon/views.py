from django.shortcuts import render, redirect
from django.urls import reverse
from django.contrib.auth.decorators import login_required

from .models import *

# Create your views here.
from .utils import purchase


# Home page, used to show a list of items.
def home(request):
    context = {}
    items = Item.objects.all()
    if request.method == "POST":
        search = request.POST["search"]
        items = items.filter(description__icontains=search)
    context["items"] = items
    return render(request, "amazon/home.html", context)


# Item detail page, used to show the detail info of one specific item.
def item_detail(request, item_id):
    item = Item.objects.get(pk=item_id)
    context = {}
    if request.method == "POST":
        if not request.user.is_authenticated:
            return redirect(reverse("login"))
        cnt = int(request.POST["count"])
        if request.POST["action"] == "buy":
            print("user buy thing" + str(cnt))
            # create a new package
            package = Package(owner=request.user)
            package.save()
            package.orders.create(
                owner=request.user,
                item=item,
                cnt=cnt
            )
            return redirect(reverse("checkout", kwargs={'package_id': package.id}))
        else:
            print("user add thing" + str(cnt))
            try:
                exist_order = Order.objects.get(owner=request.user, item=item, package__isnull=True)
                exist_order.cnt += cnt
                exist_order.save()
                print("existing order")
            except Order.DoesNotExist:
                # create a new order
                order = Order(owner=request.user, item=item, cnt=cnt)
                order.save()
                print("create new order")
            context["info"] = "Successfully add to cart."
            return render(request, "amazon/success.html", context)
    else:
        context["item"] = item
        return render(request, "amazon/item_detail.html", context)


@login_required
def checkout(request, package_id):
    package = Package.objects.get(pk=package_id)
    context = {}
    # actually checkout
    if request.method == "POST":
        x = request.POST["x"]
        y = request.POST["y"]
        package.dest_x = x
        package.dest_y = y
        package.save()
        print(package.dest_x + "  " + package.dest_y)
        print("checkout!")
        context["info"] = "Purchase successful."
        return render(request, "amazon/success.html", context)
    else:
        context["total"] = package.total()
        context["package"] = package
        return render(request, "amazon/checkout.html", context)


@login_required
def shop_cart(request):
    orders = Order.objects.filter(owner=request.user).filter(package__isnull=True)
    if request.method == 'POST':
        operation = request.POST["operation"]
        if operation == "delete":
            oid = request.POST["order_id"]
            orders.get(pk=oid).delete()
        elif operation == "checkout":
            # get all checked orders
            checked_orders = request.POST.getlist("checked_orders")
            # TODO: create a new package, purchase and return to successful page
            pack = Package(owner=request.user, warehouse=1)
            for o in checked_orders:
                pack.orders.add(orders.get(pk=o))
                print(orders.get(pk=o))
            print(checked_orders)
            # return redirect(reverse("checkout", kwargs={'package_id': package.id}))
            return redirect(reverse("checkout", kwargs={'package_id': 3}))
    total = 0
    for o in orders:
        total += o.total()
    context = {"orders": orders, "total": total}
    return render(request, "amazon/shopping_cart.html", context)


def new_order(request):
    return render(request, 'amazon/new_order.html')

@login_required
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
    return render(request, 'amazon/success.html', {"info": "Order successful!"})

@login_required
def list_package(request):
    package_list = Package.objects.filter(owner=request.user).order_by('creation_time')
    item_dict = {}
    price_dict = {}

    for pack in package_list:
        orders = Order.objects.filter(package__id=pack.id)
        item_dict[pack.id] = orders
        total = 0
        for o in orders:
            total += o.total()
        price_dict[pack.id] = total

    context = {
        'package_list': package_list,
        'item_dict':item_dict,
        'price_dict':price_dict
    }
    return render(request, 'amazon/list_package.html', context)

@login_required
def list_package_detail(request, package_id):
    orders = Order.objects.filter(package__id = package_id)
    total = 0
    for o in orders:
        total += o.total()
    context = {
        'product_list': orders,
        'pack': Package.objects.get(owner=request.user, id=package_id),
        'total':total
    }
    return render(request, 'amazon/list_package_detail.html', context)
