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

func (l *LokiClient) Push(event LogEvent) error {
	payload := lokiPushRequest{
		Streams: []lokiStream{
			{
				Stream: map[string]string{
					"job":       "logstream",
					"device_id": event.DeviceID,
					"priority":  event.Priority,
					"tag":       event.Tag,
				},
				Values: [][2]string{
					{
						strconv.FormatInt(
							event.Timestamp*int64(time.Millisecond),
							10,
						),
						event.Message,
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
