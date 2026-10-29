import (
	"bytes"
	"encoding/json"
)

type UUID = string

type Date string

type Patch[T any] struct {
	Set   bool
	Value T
}

func (p Patch[T]) IsZero() bool { return !p.Set }

func (p Patch[T]) MarshalJSON() ([]byte, error) { return json.Marshal(p.Value) }

// A Patch is only ever read from a request body someone typed, and a decoder's DisallowUnknownFields does not reach
// past a custom UnmarshalJSON, so the nested value has to check its fields itself
func (p *Patch[T]) UnmarshalJSON(data []byte) error {
	p.Set = true
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	return decoder.Decode(&p.Value)
}
