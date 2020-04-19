from django.shortcuts import render, redirect
from django.urls import reverse
from django.contrib.auth.decorators import login_required
from django.http import JsonResponse
from amazon.utils import *

from .models import *

# Create your views here.
from .utils import purchase


# Home page, used to show a list of items.
def home(request):
    context = {}
    items = Item.objects.all().order_by("id")
    if request.method == "POST":
        search = request.POST["search"]
        items = items.filter(description__icontains=search)
    context["items"] = items
    context["categories"] = Category.objects.all()
    context["category"] = "All"
    return render(request, "amazon/home.html", context)


# Home page, but with specific category
def home_category(request, category):
    context = {}
    category = Category.objects.get(category=category)
    items = Item.objects.all().order_by("id").filter(category=category)
    if request.method == "POST":
        search = request.POST["search"]
        items = items.filter(description__icontains=search)
    context["items"] = items
    context["categories"] = Category.objects.all()
    context["category"] = category
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
            # create a new package
            package = Package(owner=request.user)
            package.save()
            package.orders.create(
                owner=request.user,
                item=item,
                item_cnt=cnt
            )
            return redirect(reverse("checkout", kwargs={'package_id': package.id}))
        else:
            try:
                # try to get an existing order
                exist_order = Order.objects.get(owner=request.user, item=item, package__isnull=True)
                exist_order.item_cnt += cnt
                exist_order.save()
            except Order.DoesNotExist:
                # create a new order
                order = Order(owner=request.user, item=item, item_cnt=cnt)
                order.save()
            context["info"] = "Successfully add to cart."
            context["is_add_cart"] = True
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
        context["is_checkout"] = True
        # once user checkout, the price will be final price
        #for order in package.orders:
        #    order.item_price = order.item.price
        # TODO: send the buy request to daemon
        purchase(package.id)
        return render(request, "amazon/success.html", context)
    else:
        context["total"] = package.total()
        context["package"] = package
        return render(request, "amazon/checkout.html", context)


@login_required
def shop_cart(request):
    orders = Order.objects.filter(owner=request.user).filter(package__isnull=True).order_by("creation_time")
    if request.method == 'POST':
        operation = request.POST["operation"]
        # user delete some order
        if operation == "delete":
            oid = request.POST["order_id"]
            orders.get(pk=oid).delete()
        elif operation == "checkout":
            # get all checked orders
            checked_orders = request.POST.getlist("checked_orders")
            print(checked_orders)
            # will only create a new package when at least one order is chosen
            if len(checked_orders) > 0:
                pack = Package(owner=request.user, warehouse=1)
                pack.save()
                for o in checked_orders:
                    print(orders.get(pk=int(o)))
                    pack.orders.add(orders.get(pk=int(o)))
                return redirect(reverse("checkout", kwargs={'package_id': pack.id}))
        # api for calculating the total price
        elif operation == "cal_total" and request.is_ajax():
            checked_orders = request.POST.getlist("checked_orders")
            total = 0.0
            for o in checked_orders:
                total += orders.get(pk=o).total()
            return JsonResponse({"total_cart": ("%.2f" % total)})
    total = 0
    for o in orders:
        total += o.total()
    context = {"orders": orders, "total": total}
    return render(request, "amazon/shopping_cart.html", context)


# ajax api for changing item count in the shopping cart
@login_required
def change_cnt(request):
    if request.is_ajax() and request.method == "POST":
        order_id = request.POST["order_id"]
        operation = request.POST["operation"]
        total_cart = float(request.POST["total_cart"])
        order = Order.objects.get(pk=order_id)
        # lower and upper limit --- 1 ~ 99
        if operation == "add" and order.item_cnt < 99:
            order.item_cnt += 1
            order.save()
            total_cart += order.item.price
        elif operation == "minus" and order.item_cnt > 1:
            order.item_cnt -= 1
            order.save()
            total_cart -= order.item.price
        data = {
            # latest count
            "cnt": order.item_cnt,
            # total price for the order
            "total_order": ("%.2f" % order.total()),
            # total price for all
            "total_cart": ("%.2f" % total_cart)
        }
        return JsonResponse(data)
    return JsonResponse({})


@login_required
def list_package(request):
    package_list = Package.objects.filter(owner=request.user).order_by('creation_time')
    item_dict = {}

    for pack in package_list:
        orders = Order.objects.filter(package__id=pack.id)
        item_dict[pack.id] = orders

    context = {
        'package_list': package_list,
        'item_dict':item_dict,
    }
    return render(request, 'amazon/list_package.html', context)


@login_required
def list_package_detail(request, package_id):
    context = {
        'product_list': Order.objects.filter(package__id=package_id),
        'pack': Package.objects.get(owner=request.user, id=package_id),
    }
    return render(request, 'amazon/list_package_detail.html', context)


# check whether an item has already exist
@login_required
def check_item(request):
    if request.is_ajax() and request.method == "POST":
        new_item = request.POST["item_description"]
        try:
            Item.objects.get(description=new_item)
            data = {"exist": True}
        except Item.DoesNotExist:
            data = {"exist": False}
        return JsonResponse(data)
    return JsonResponse({})


@login_required
def add_item(request):
    if request.method == "POST":
        p = request.FILES["thumbnail"]
        description = request.POST["description"]
        price = float(request.POST["price"])
        category = request.POST.getlist("category")[0]
        save_img(description + p.name.split(".")[1], p)
        # check whether it's a new category
        try:
            c = Category.objects.get(category=category)
        except Category.DoesNotExist:
            c = Category(category=category)
            c.save()
        new_item = Item(description=description, price=price, category=c, img="/static/img/" + description + p.name.split(".")[1])
        new_item.save()
        return render(request, "amazon/success.html")

    context = {}
    categories = Category.objects.all()
    context["categories"] = categories
    return render(request, "amazon/add_item.html", context)
