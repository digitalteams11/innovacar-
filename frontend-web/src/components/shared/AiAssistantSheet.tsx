import MobileBottomSheet from '../MobileBottomSheet';
import AiChatPanel from './AiChatPanel';

interface AiAssistantSheetProps {
  isOpen: boolean;
  onClose: () => void;
  module?: string;
}

/**
 * Mobile bottom-sheet wrapper around the existing AiChatPanel — same chat
 * logic/UI as the desktop floating card, just re-parented into the shared
 * MobileBottomSheet frame (backdrop, safe-area docking, Escape/outside-click
 * to close) instead of AiChatPanel's own fixed positioning. No title is
 * passed to MobileBottomSheet: AiChatPanel already renders its own header
 * with a close button, so this avoids a duplicate header.
 */
export default function AiAssistantSheet({ isOpen, onClose, module }: AiAssistantSheetProps) {
  return (
    <MobileBottomSheet isOpen={isOpen} onClose={onClose} maxHeightClass="h-[85dvh] max-h-[85dvh]" showDragHandle>
      <AiChatPanel module={module} onClose={onClose} variant="sheet" />
    </MobileBottomSheet>
  );
}
