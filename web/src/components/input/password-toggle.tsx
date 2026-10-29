import { EyeIcon, EyeOffIcon } from "lucide-react"
import { useState } from "react"
import { Button } from "@dewey/components/ui/button"

export const usePasswordToggle = () => {
  const [visible, setVisible] = useState(false)
  return {
    type: visible ? "text" : "password",
    toggle: (
      <Button
        excludeFromTabOrder
        variant="ghost"
        size="icon"
        aria-label={visible ? "Hide password" : "Show password"}
        className="my-2 ml-2 h-full"
        onPress={() => setVisible(previous => !previous)}
      >
        {visible ? <EyeOffIcon /> : <EyeIcon />}
      </Button>
    ),
  }
}
