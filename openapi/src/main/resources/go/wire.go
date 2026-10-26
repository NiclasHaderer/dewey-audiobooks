import "encoding/json"

// UUID is the textual form the server sends, a plain string so callers need no extra dependency
type UUID = string

// Date is a calendar day as the server writes it, YYYY-MM-DD
type Date string

// Patch is a field of an update that may be left out. An unset Patch is not sent at all, which leaves the value on
// the server as it is; a set one is sent even when its value is the zero value or nil.
type Patch[T any] struct {
	Set   bool
	Value T
}

func PatchOf[T any](value T) Patch[T] { return Patch[T]{Set: true, Value: value} }

// IsZero is what the omitzero tag of a Patch field asks, so an unset Patch leaves its key out of the JSON
func (p Patch[T]) IsZero() bool { return !p.Set }

func (p Patch[T]) MarshalJSON() ([]byte, error) { return json.Marshal(p.Value) }

func (p *Patch[T]) UnmarshalJSON(data []byte) error {
	p.Set = true
	return json.Unmarshal(data, &p.Value)
}
