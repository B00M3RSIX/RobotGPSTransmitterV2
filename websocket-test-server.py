#!/usr/bin/env python3

import asyncio
import websockets
import json
import argparse
import signal
import sys
from datetime import datetime

# Global variables
connected_clients = set()
message_count = 0

async def handle_client(websocket):
    """Handle a connection from a client"""
    global connected_clients, message_count
    client_id = f"Client-{len(connected_clients) + 1}"
    
    print(f"\n[{datetime.now().strftime('%H:%M:%S')}] New connection from {websocket.remote_address[0]}:{websocket.remote_address[1]} ({client_id})")
    connected_clients.add(websocket)
    
    try:
        print(f"Waiting for messages from {client_id}...")
        async for message in websocket:
            message_count += 1
            
            # Try to parse as JSON
            try:
                data = json.loads(message)
                print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Message #{message_count} from {client_id}:")
                print(json.dumps(data, indent=2))
                
                # Look for specific GPS data if it matches the expected format
                if 'op' in data and data['op'] == 'publish' and 'msg' in data:
                    msg = data['msg']
                    if 'latitude' in msg and 'longitude' in msg:
                        print("\nExtracted GPS data:")
                        print(f"  Topic:     {data.get('topic', 'N/A')}")
                        print(f"  Latitude:  {msg['latitude']}")
                        print(f"  Longitude: {msg['longitude']}")
                        if 'altitude' in msg:
                            print(f"  Altitude:  {msg['altitude']} m")
            except json.JSONDecodeError:
                # Not JSON, print as text
                print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Message #{message_count} from {client_id} (raw text):")
                print(message[:200] + ('...' if len(message) > 200 else ''))
            
            # Send a simple acknowledgment to the client
            if args.respond:
                response = {
                    "status": "received",
                    "message_id": message_count,
                    "timestamp": datetime.now().isoformat()
                }
                await websocket.send(json.dumps(response))
                print(f"Response sent to {client_id}")
            
    except websockets.exceptions.ConnectionClosed as e:
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Connection from {client_id} closed: Code {e.code}, Reason: {e.reason}")
    except Exception as e:
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] Error handling {client_id}: {e}")
    finally:
        connected_clients.remove(websocket)
        print(f"Client {client_id} disconnected. {len(connected_clients)} clients remaining.")

def print_server_info(host, port):
    """Print server information including IP addresses"""
    import socket
    print("\n========== WebSocket Test Server ==========")
    print(f"Listening on port: {port}")
    print("\nIP Addresses to connect to:")
    
    # Get all network interfaces
    try:
        hostname = socket.gethostname()
        print(f"  Hostname: {hostname}")
        
        # Get IP addresses
        addresses = []
        
        # Try to get all interfaces
        try:
            for info in socket.getaddrinfo(hostname, None):
                addr = info[4][0]
                if addr != '127.0.0.1' and not addr.startswith('::') and ':' not in addr:
                    addresses.append(addr)
        except:
            pass
        
        # If no addresses found, fall back to simpler method
        if not addresses:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            try:
                # Use Google's public DNS to determine local IP
                s.connect(('8.8.8.8', 80))
                addresses = [s.getsockname()[0]]
            except:
                pass
            finally:
                s.close()
        
        # Print all found addresses
        if addresses:
            for addr in sorted(set(addresses)):
                print(f"  http://{addr}:{port}  (use ws://{addr}:{port} in your app)")
        else:
            print("  Could not determine IP address. Use your local network IP address.")
    except Exception as e:
        print(f"  Error getting network info: {e}")
    
    print("\nPress Ctrl+C to exit")
    print("=========================================\n")

async def main():
    """Main function to run the WebSocket server"""
    # Print server info including IP addresses
    print_server_info(args.host, args.port)
    
    # Create the WebSocket server
    async with websockets.serve(handle_client, args.host, args.port):
        # Keep the server running until interrupted
        stop = asyncio.Future()
        await stop  # This will run forever until cancelled

if __name__ == "__main__":
    # Parse command line arguments
    parser = argparse.ArgumentParser(description='Simple WebSocket server for testing')
    parser.add_argument('--host', default='0.0.0.0', help='Host to bind to (default: 0.0.0.0)')
    parser.add_argument('--port', type=int, default=9090, help='Port to listen on (default: 9090)')
    parser.add_argument('--respond', action='store_true', help='Send acknowledgment responses to clients')
    
    args = parser.parse_args()
    
    try:
        # Run the asyncio event loop with our main function
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nKeyboard interrupt received. Shutting down...")
    except Exception as e:
        print(f"Error: {e}")
    finally:
        print("Server shut down.")
