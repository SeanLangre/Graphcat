package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/coder/websocket"
)

type LogEvent struct {
	DeviceID  string `json:"device_id"`
	Seq       uint64 `json:"seq"`
	Timestamp int64  `json:"timestamp"`
	Priority  string `json:"priority"`
	Tag       string `json:"tag"`
	PID       int    `json:"pid"`
	TID       int    `json:"tid"`
	UID       int    `json:"uid"`
	Package   string `json:"package"`
	Message   string `json:"message"`
}

type Hub struct {
	mu      sync.RWMutex
	clients map[*websocket.Conn]struct{}
}

func NewHub() *Hub {
	return &Hub{
		clients: make(map[*websocket.Conn]struct{}),
	}
}

func (h *Hub) add(conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()

	h.clients[conn] = struct{}{}
}

func (h *Hub) remove(conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()

	delete(h.clients, conn)
}

func (h *Hub) broadcast(event LogEvent) {
	data, err := json.Marshal(event)
	if err != nil {
		log.Printf("failed to encode event: %v", err)
		return
	}

	h.mu.RLock()
	defer h.mu.RUnlock()

	for conn := range h.clients {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)

		err := conn.Write(ctx, websocket.MessageText, data)

		cancel()

		if err != nil {
			log.Printf("failed to send to browser: %v", err)
		}
	}
}

func (h *Hub) deviceHandler(
	w http.ResponseWriter,
	r *http.Request,
	loki *LokiClient,
) {
	log.Printf("device request received: %s %s", r.Method, r.URL.Path)
	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		log.Printf("device websocket: %v", err)
		return
	}
	defer conn.Close(websocket.StatusNormalClosure, "")

	log.Printf("device connected: %s", r.RemoteAddr)

	for {
		_, data, err := conn.Read(r.Context())
		if err != nil {
			log.Printf("device disconnected: %s", r.RemoteAddr)
			return
		}

		var event LogEvent

		if err := json.Unmarshal(data, &event); err != nil {
			log.Printf("invalid log event: %v", err)
			continue
		}

		if err := loki.Push(event); err != nil {
			log.Printf("failed to push to Loki: %v", err)
		}

		log.Printf(
			"log from %s: [%s] %s",
			event.DeviceID,
			event.Priority,
			event.Message,
		)

		h.broadcast(event)
	}
}

func (h *Hub) dashboardHandler(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		log.Printf("dashboard websocket: %v", err)
		return
	}
	defer conn.Close(websocket.StatusNormalClosure, "")

	h.add(conn)
	defer h.remove(conn)

	log.Printf("dashboard connected: %s", r.RemoteAddr)

	// Keep the connection alive.
	for {
		_, _, err := conn.Read(r.Context())
		if err != nil {
			log.Printf("dashboard disconnected: %s", r.RemoteAddr)
			return
		}
	}
}

func main() {
	lokiURL := os.Getenv("LOKI_URL")

	if lokiURL == "" {
		lokiURL = "http://localhost:3100"
	}

	log.Printf("using Loki at %s", lokiURL)

	loki := NewLokiClient(lokiURL)

	hub := NewHub()

	http.HandleFunc("/v1/device/stream", func(w http.ResponseWriter, r *http.Request) {
		hub.deviceHandler(w, r, loki)
	})

	http.HandleFunc("/v1/dashboard/stream", hub.dashboardHandler)

	http.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("ok\n"))
	})

	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		http.ServeFile(w, r, "index.html")
	})

	addr := ":8080"

	log.Printf("server listening on %s", addr)

	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatal(err)
	}
}
