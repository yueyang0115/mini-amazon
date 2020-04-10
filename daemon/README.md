# Daemon

This is the amazonDaemon who keep running in the background, and has no user interface. It's mainly responsible for communicate with the world simulator, and it will share the same database with front-end amazon.

You should run this amazonDaemon with the front-end amazon together(aka on the same machine).

## If you want to purchase some items

1. connect to `localhost`, port `34567`
2. send a list of item you want to purchase
3. wait for response or close the socket