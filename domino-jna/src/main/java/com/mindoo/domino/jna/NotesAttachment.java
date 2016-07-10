package com.mindoo.domino.jna;

import java.util.Calendar;

import com.mindoo.domino.jna.NotesAttachment.IDataCallback.Action;
import com.mindoo.domino.jna.constants.Compression;
import com.mindoo.domino.jna.errors.INotesErrorConstants;
import com.mindoo.domino.jna.errors.NotesError;
import com.mindoo.domino.jna.errors.NotesErrorUtils;
import com.mindoo.domino.jna.internal.NotesCAPI;
import com.mindoo.domino.jna.internal.NotesCAPI.NoteExtractCallback;
import com.mindoo.domino.jna.internal.NotesJNAContext;
import com.mindoo.domino.jna.internal.WinNotesCAPI;
import com.mindoo.domino.jna.structs.NotesBlockId;
import com.mindoo.domino.jna.structs.NotesTimeDate;
import com.sun.jna.Pointer;

/**
 * Data container to access metadata and binary data of a note attachment
 * 
 * @author Karsten Lehmann
 */
public class NotesAttachment {
	private String m_fileName;
	private Compression m_compression;
	private short m_fileFlags;
	private int m_fileSize;
	private NotesTimeDate m_fileCreated;
	private NotesTimeDate m_fileModified;
	private NotesNote m_parentNote;
	private NotesBlockId m_itemBlockId;

	public NotesAttachment(String fileName, Compression compression, short fileFlags, int fileSize,
			NotesTimeDate fileCreated, NotesTimeDate fileModified, NotesNote parentNote,
			NotesBlockId itemBlockId) {
		m_fileName = fileName;
		m_compression = compression;
		m_fileFlags = fileFlags;
		m_fileSize = fileSize;
		m_fileCreated = fileCreated;
		m_fileModified = fileModified;
		m_parentNote = parentNote;
		m_itemBlockId = itemBlockId;
	}
	
	/**
	 * Returns the filename of the attachment
	 * 
	 * @return filename
	 */
	public String getFileName() {
		return m_fileName;
	}
	
	/**
	 * Returns the compression type
	 * 
	 * @return compression
	 */
	public Compression getCompression() {
		return m_compression;
	}
	
	/**
	 * Returns file flags, e.g. {@link NotesCAPI#FILEFLAG_SIGN}
	 * 
	 * @return flags
	 */
	public short getFileFlags() {
		return m_fileFlags;
	}
	
	/**
	 * Returns the file size
	 * 
	 * @return file size
	 */
	public int getFileSize() {
		return m_fileSize;
	}
	
	/**
	 * Returns the creation date
	 * 
	 * @return creation date
	 */
	public Calendar getFileCreated() {
		return m_fileCreated.toCalendar();
	}
	
	/**
	 * Returns the last modified date
	 * 
	 * @return date
	 */
	public Calendar getFileModified() {
		return m_fileModified.toCalendar();
	}

	/**
	 * Returns the parent note of the attachment
	 * 
	 * @return note
	 */
	public NotesNote getParentNote() {
		return m_parentNote;
	}
	
	/**
	 * Method to access the binary attachment data
	 * 
	 * @param callback callback is called with streamed data
	 */
	public void readData(final IDataCallback callback) {
		m_parentNote.checkHandle();
		
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		NotesBlockId.ByValue itemBlockIdByVal = new NotesBlockId.ByValue();
		itemBlockIdByVal.pool = m_itemBlockId.pool;
		itemBlockIdByVal.block = m_itemBlockId.block;
		
		int extractFlags = 0;
		int hDecryptionCipher = 0;
		
		NoteExtractCallback extractCallback;
		final Throwable[] extractError = new Throwable[1];
		
		if (notesAPI instanceof WinNotesCAPI) {
			extractCallback = new WinNotesCAPI.NoteExtractCallbackWin() {
				
				@Override
				public short invoke(Pointer data, int length, Pointer param) {
					try {
						byte[] dataArr = data.getByteArray(0, length);
						Action action = callback.read(dataArr);
						if (action==Action.Continue) {
							return 0;
						}
						else {
							throw new InterruptedException();
						}
					}
					catch (Throwable t) {
						extractError[0] = t;
						return INotesErrorConstants.ERR_NSF_INTERRUPT;
					}
				}
			};
		}
		else {
			extractCallback = new NoteExtractCallback() {

				@Override
				public short invoke(Pointer data, int length, Pointer param) {
					try {
						byte[] dataArr = data.getByteArray(0, length);
						Action action = callback.read(dataArr);
						if (action==Action.Continue) {
							return 0;
						}
						else {
							throw new InterruptedException();
						}
					}
					catch (Throwable t) {
						extractError[0] = t;
						return INotesErrorConstants.ERR_NSF_INTERRUPT;
					}
				}
			};
		}
		
		short result;
		if (NotesJNAContext.is64Bit()) {
			result = notesAPI.b64_NSFNoteCipherExtractWithCallback(m_parentNote.getHandle64(), 
					itemBlockIdByVal, extractFlags, hDecryptionCipher, 
					extractCallback, null, 0, null);
		}
		else {
			result = notesAPI.b32_NSFNoteCipherExtractWithCallback(m_parentNote.getHandle32(), 
					itemBlockIdByVal, extractFlags, hDecryptionCipher, 
					extractCallback, null, 0, null);
		}
		
		if (extractError[0] != null) {
			throw new NotesError(0, "Extraction interrupted", extractError[0]);
		}
		
		NotesErrorUtils.checkResult(result);
	}
	
	/**
	 * Deletes an attached file item from a note and also deallocates the disk space
	 * used to store the attached file in the database.
	 */
	public void deleteFromNote() {
		m_parentNote.checkHandle();
		
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		NotesBlockId.ByValue itemBlockIdByVal = new NotesBlockId.ByValue();
		itemBlockIdByVal.pool = m_itemBlockId.pool;
		itemBlockIdByVal.block = m_itemBlockId.block;

		if (NotesJNAContext.is64Bit()) {
			short result = notesAPI.b64_NSFNoteDetachFile(m_parentNote.getHandle64(), itemBlockIdByVal);
			NotesErrorUtils.checkResult(result);
		}
		else {
			short result = notesAPI.b32_NSFNoteDetachFile(m_parentNote.getHandle32(), itemBlockIdByVal);
			NotesErrorUtils.checkResult(result);
		}
	}
	
	/**
	 * Callback class to read the streamed attachment data
	 * 
	 * @author Karsten Lehmann
	 */
	public static interface IDataCallback {
		public static enum Action {Continue, Stop};
		
		/**
		 * Implement this method to receive attachment data
		 * 
		 * @param data data
		 * @return action, either Continue or Stop
		 */
		public Action read(byte[] data);
	}
}
