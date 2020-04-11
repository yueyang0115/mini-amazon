import socket


# Tell the daemon to purchase something, which specify by the package id.
# front-end should first store the package into DB and then notify the daemon by sending the id.
def purchase(package_id):
    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    # use port 8888 to communicate with daemon
    client.connect(('localhost', 8888))
    # NOTE: append a \n at the end to become a line
    msg = str(package_id) + '\n'
    client.send(msg.encode('utf-8'))
    # expected response: ack:<package_id>
    data = client.recv(1024)
    data = data.decode()
    res = data.split(":")
    if res[0] == "ack" and res[1] == str(package_id):
        return True
    print('recv:', data)
    return False


if __name__ == '__main__':
    purchase(1)
