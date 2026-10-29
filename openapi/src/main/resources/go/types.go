import (
	"bytes"
	"encoding/json"
	"reflect"
)

type UUID = string

type Date string

// A list that may not be null, so its nil is written as []
type List[T any] []T

func (l List[T]) MarshalJSON() ([]byte, error) {
	if l == nil {
		return []byte("[]"), nil
	}
	return json.Marshal([]T(l))
}

func (List[T]) notNull() {}

// A map that may not be null, so its nil is written as {}
type Map[K comparable, V any] map[K]V

func (m Map[K, V]) MarshalJSON() ([]byte, error) {
	if m == nil {
		return []byte("{}"), nil
	}
	return json.Marshal(map[K]V(m))
}

func (Map[K, V]) notNull() {}

// An unset Patch is left out of the JSON, a set one is written with its value, null included
type Patch[T any] struct {
	Set   bool
	Value T
}

func (p Patch[T]) IsZero() bool { return !p.Set }

func (p Patch[T]) MarshalJSON() ([]byte, error) { return json.Marshal(p.Value) }

// A Patch is only ever read from a request body someone typed, and a decoder's DisallowUnknownFields does not reach
// past a custom UnmarshalJSON, so the nested value has to check its fields itself. That is also why List and Map have
// no UnmarshalJSON of their own: it would read the items without the check.
//
// A null is only taken where the value may be null. json would otherwise leave a string empty or a number zero, and
// the server would then store that.
func (p *Patch[T]) UnmarshalJSON(data []byte) error {
	if string(bytes.TrimSpace(data)) == "null" && !mayBeNull[T]() {
		return &json.UnmarshalTypeError{Value: "null", Type: reflect.TypeFor[T]()}
	}
	p.Set = true
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	return decoder.Decode(&p.Value)
}

func mayBeNull[T any]() bool {
	var value T
	if _, notNull := any(value).(interface{ notNull() }); notNull {
		return false
	}
	switch reflect.TypeFor[T]().Kind() {
	case reflect.Pointer, reflect.Interface, reflect.Slice, reflect.Map:
		return true
	}
	return false
}
