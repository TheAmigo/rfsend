import time
from machine import Pin, Timer, reset
from mqtt_as import MQTTClient, config
import uasyncio as asyncio
import ujson as json
import re

topic = 'rfsend'

#TODO: why doesn't this work?
#with open('rfsend.cfg') as cfg:
#    config.update(json.loads(cfg.read()))
config['ssid']      = 'WIFI_SSID'     # Change to your WiFi SSID
config['wifi_pw']   = 'WIFI_PASSWORD' # Change to your WiFi password
config['server']    = '192.168.0.187' # Change to your MQTT broker's IP
config['queue_len'] = 1

topic_req = f'cmd/{topic}/req'
topic_resp = f'cmd/{topic}/resp'
rf_pwr   = Pin(2, Pin.OUT)
rf_xmit  = Pin(3, Pin.OUT)
raw_data = re.compile("^RAW_Data: ")
led = Pin("LED", Pin.OUT)

def send_cmd(cmd):
    rf_data = load_file(cmd)
    xmit_data(rf_data)
    mqtt_pub(json.dumps(cmd))
    
def load_file(cmd):
    rf_data = []
    file_name = f'buttons/{cmd["button"]}.sub'
    print(f"Loading RF data from file {file_name}")
    with open(file_name) as reader:
        for line in reader.readlines():
            if raw_data.match(line):
                rf_data = rf_data + list(map(int, line.split()[1:]))
    return rf_data

def xmit_data(rf_data):
    global rf_pwr, rf_xmit
    led.on()
    rf_pwr.on()
    print(f"Transmitting data...")
    delay_total = 0
    start = time.ticks_us()
    for delay in rf_data:
        if delay > 0:
            rf_xmit.on()
        else:
            rf_xmit.off()
        delay_total = delay_total + abs(delay)
        duration = delay_total - time.ticks_diff(time.ticks_us(), start)
        time.sleep_us(duration)
    print(f"Done transmitting")
    rf_pwr.off()
    led.off()
    
def mqtt_pub(msg):
    asyncio.create_task(mqtt.publish(topic_resp, msg))

async def mqtt_recv(client):
    async for topic, msg, retained in client.queue:
        try:
            cmd = json.loads(msg)
            print(f"topic = {topic}: button = {cmd['button']}")
            send_cmd(cmd)
        except:
            pass

async def mqtt_up(client):
    global topic_req
    while True:
        await client.up.wait()
        client.up.clear()
        print(f"subscribing to {topic_req}")
        await client.subscribe(topic_req, 1)
        led.off()

async def main(mqtt):
    await mqtt.connect()
    asyncio.create_task(mqtt_up(mqtt))
    asyncio.create_task(mqtt_recv(mqtt))
    await asyncio.sleep(0)
    while True:
        await asyncio.sleep(10)

mqtt = MQTTClient(config)
try:
    asyncio.run(main(mqtt))
finally:
    for i in range(5):
        led.on()
        time.sleep(0.5)
        led.off()
        time.sleep(0.5)
    reset()
