package main

import (
	"context"
	"encoding/json"
	"log"
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
	UID       int    `json:"uid"`
	Message   string `json:"message"`
}

func main() {
	ctx := context.Background()

	conn, _, err := websocket.Dial(
		ctx,
		"ws://localhost:8080/v1/device/stream",
		nil,
	)
	if err != nil {
		log.Fatal(err)
	}
	defer conn.Close(websocket.StatusNormalClosure, "")

	for seq := uint64(1); ; seq++ {
		event := LogEvent{
			DeviceID:  "test-phone",
			Seq:       seq,
			Timestamp: time.Now().UnixMilli(),
			Priority:  "E",
			Tag:       "TestApp",
			PID:       1234,
			UID:       1000,
			Message:   "Hello from the test phone",
		}

		data, err := json.Marshal(event)
		if err != nil {
			log.Fatal(err)
		}

		err = conn.Write(
			ctx,
			websocket.MessageText,
			data,
		)
		if err != nil {
			log.Fatal(err)
		}

		log.Printf("sent event %d", seq)

		time.Sleep(time.Second)
	}
}
