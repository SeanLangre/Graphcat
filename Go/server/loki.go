package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"
)

type LokiClient struct {
	url    string
	client *http.Client
}

func NewLokiClient(url string) *LokiClient {
	return &LokiClient{
		url: url,
		client: &http.Client{
			Timeout: 5 * time.Second,
		},
	}
}

type lokiPushRequest struct {
	Streams []lokiStream `json:"streams"`
}

type lokiStream struct {
	Stream map[string]string `json:"stream"`
	Values [][2]string       `json:"values"`
}

// lokiLevels maps Android log priorities to the level names Loki and
// Grafana recognise, so lines aren't shown as "unknown".
var lokiLevels = map[string]string{
	"V": "trace",
	"D": "debug",
	"I": "info",
	"W": "warn",
	"E": "error",
	"F": "critical",
}

func (l *LokiClient) Push(event LogEvent) error {
	app := event.Package
	if app == "" {
		app = "unknown"
	}

	level, ok := lokiLevels[event.Priority]
	if !ok {
		level = "unknown"
	}

	// Mirror `adb logcat` so the source is visible in the line itself,
	// not only in the stream labels.
	line := fmt.Sprintf(
		"[%s] %s/%s(%d-%d): %s",
		app,
		event.Priority,
		event.Tag,
		event.PID,
		event.TID,
		event.Message,
	)

	payload := lokiPushRequest{
		Streams: []lokiStream{
			{
				Stream: map[string]string{
					"job":       "graphcat",
					"device_id": event.DeviceID,
					"priority":  event.Priority,
					"level":     level,
					"tag":       event.Tag,
					"app":       app,
				},
				Values: [][2]string{
					{
						strconv.FormatInt(
							event.Timestamp*int64(time.Millisecond),
							10,
						),
						line,
					},
				},
			},
		},
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	req, err := http.NewRequest(
		http.MethodPost,
		l.url+"/loki/api/v1/push",
		bytes.NewReader(body),
	)
	if err != nil {
		return err
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := l.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf(
			"Loki returned HTTP %d",
			resp.StatusCode,
		)
	}

	return nil
}
