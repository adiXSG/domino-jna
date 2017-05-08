package com.mindoo.domino.jna.ecl;

import java.util.EnumSet;
import java.util.List;

import com.mindoo.domino.jna.NotesNamesList;
import com.mindoo.domino.jna.NotesNote;
import com.mindoo.domino.jna.errors.NotesError;
import com.mindoo.domino.jna.errors.NotesErrorUtils;
import com.mindoo.domino.jna.internal.NotesCAPI;
import com.mindoo.domino.jna.internal.NotesJNAContext;
import com.mindoo.domino.jna.utils.NotesNamingUtils;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.ShortByReference;

/**
 * Utility class to read the current platforms Execution Control List (ECL)
 * 
 * @author Karsten Lehmann
 */
public class ECL {

	/** Types of ECL settings */
	public static enum ECLType {
		Lotusscript((short) NotesCAPI.ECL_TYPE_LOTUS_SCRIPT),
		JavaApplets((short) NotesCAPI.ECL_TYPE_JAVA_APPLET),
		Javascript((short) NotesCAPI.ECL_TYPE_JAVASCRIPT);
	
		private short m_type;
		
		private ECLType(short type) {
			m_type = type;
		}
		
		public short getTypeAsShort() {
			return m_type;
		}
	};
	
	/**
	 * Returns an instance to compute the ECL for a username
	 * 
	 * @param eclType ECL type
	 * @param userName username either abbreviated or canonical
	 * @return ECL
	 */
	public static ECL getInstance(ECLType eclType, String userName) {
		return new ECL(eclType, userName);
	}

	/**
	 * Returns an instance to compute the ECL for a given {@link NotesNamesList}
	 * 
	 * @param eclType ECL type
	 * @param namesList names list
	 * @return ECL
	 */
	public static ECL getInstance(ECLType eclType, NotesNamesList namesList) {
		return new ECL(eclType, namesList);
	}

	/**
	 * Returns an instance to compute the ECL for a manual usernameslist
	 * 
	 * @param eclType ECL type
	 * @param namesList usernameslist, e.g. with the same content as @UserNamesList . Gets converted to {@link NotesNamesList} via {@link NotesNamingUtils#writeNewNamesList(List)}
	 * @return ECL
	 */
	public static ECL getInstance(ECLType eclType, List<String> namesList) {
		return new ECL(eclType, namesList);
	}

	private ECLType m_eclType;
	private String m_userNameCanonical;
	private NotesNamesList m_namesList;
	
	private ECL(ECLType eclType, String userName) {
		m_eclType = eclType;
		m_userNameCanonical = NotesNamingUtils.toCanonicalName(userName);
	}

	private ECL(ECLType eclType, NotesNamesList namesList) {
		m_eclType = eclType;
		m_namesList = namesList;
	}

	private ECL(ECLType eclType, List<String> namesList) {
		m_eclType = eclType;
		m_namesList = NotesNamingUtils.writeNewNamesList(namesList);
	}

	@Override
	public String toString() {
		if (m_namesList!=null && m_namesList.isFreed()) {
			return "ECL [nameslist freed, ecltype="+m_eclType+"]";
		}
		else {
			return "ECL [names="+(m_userNameCanonical!=null ? m_userNameCanonical : m_namesList.getNames())+",ecltype="+m_eclType+"]";
		}
	}

	/**
	 * Reads the current capabilities
	 * 
	 * @return set of capabilities
	 */
	public EnumSet<ECLCapability> getCapabilities() {
		ShortByReference retwCapabilities = new ShortByReference();
		ShortByReference retwCapabilities2 = new ShortByReference();
		IntByReference retfUserCanModifyECL = new IntByReference();
		
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		if (m_namesList!=null) {
			Pointer pNamesList;
			long handle64=0;
			int handle32=0;
			
			if (NotesJNAContext.is64Bit()) {
				handle64 = m_namesList.getHandle64();
				if (handle64==0)
					throw new IllegalStateException("Names list already recycled");
				
				pNamesList = notesAPI.b64_OSLockObject(handle64);
			}
			else {
				handle32 = m_namesList.getHandle32();
				if (handle32==0)
					throw new IllegalStateException("Names list already recycled");
				
				pNamesList = notesAPI.b32_OSLockObject(handle32);
			}
			try {
				short result = notesAPI.ECLGetListCapabilities(pNamesList, m_eclType.getTypeAsShort(), retwCapabilities2, retwCapabilities2, retfUserCanModifyECL);
				NotesErrorUtils.checkResult(result);
			}
			finally {
				if (NotesJNAContext.is64Bit()) {
					if (handle64!=0)
						notesAPI.b64_OSUnlockObject(handle64);
				}
				else {
					if (handle32!=0)
						notesAPI.b32_OSUnlockObject(handle32);
				}
			}
		}
		else if (m_userNameCanonical!=null) {
			NotesNamesList namesList = NotesNamingUtils.buildNamesList(m_userNameCanonical);
			Pointer pNamesList=null;
			
			try {
				if (NotesJNAContext.is64Bit()) {
					pNamesList = notesAPI.b64_OSLockObject(namesList.getHandle64());
				}
				else {
					pNamesList = notesAPI.b32_OSLockObject(namesList.getHandle32());
				}
				
				short result = notesAPI.ECLGetListCapabilities(pNamesList, m_eclType.getTypeAsShort(), retwCapabilities, retwCapabilities2, retfUserCanModifyECL);
				NotesErrorUtils.checkResult(result);
			}
			finally {
				if (pNamesList!=null) {
					if (NotesJNAContext.is64Bit()) {
						notesAPI.b64_OSUnlockObject(namesList.getHandle64());
					}
					else {
						notesAPI.b32_OSUnlockObject(namesList.getHandle32());
					}
				}
				
				namesList.free();
			}
		}
		
		int retwCapabilitiesAsInt = (int) (retwCapabilities.getValue() & 0xffff);
		int retwCapabilities2AsInt = (int) (retwCapabilities2.getValue() & 0xffff);
		
		EnumSet<ECLCapability> set = toCapabilitySet(retwCapabilitiesAsInt, retwCapabilities2AsInt);
		if (retfUserCanModifyECL.getValue()==1) {
			set.add(ECLCapability.ModifyECL);
		}

		return set;
	}
	
	private static EnumSet<ECLCapability> toCapabilitySet(int wCapabilitiesAsInt, int wCapabilities2AsInt) {
		EnumSet<ECLCapability> set = EnumSet.noneOf(ECLCapability.class);
		
		for (ECLCapability currCapability : ECLCapability.values()) {
			if (currCapability==ECLCapability.ModifyECL) {
				//ignore special flag
				continue;
			}
			
			int currCapabilityInt = currCapability.getValue();
			
			if (currCapability.isWorkstationECL()) {
				if ((wCapabilities2AsInt & currCapabilityInt) == currCapabilityInt) {
					set.add(currCapability);
				}
			}
			else {
				if ((wCapabilitiesAsInt & currCapabilityInt) == currCapabilityInt) {
					set.add(currCapability);
				}
			}
		}

		return set;
	}

	/**
	 * Method to modify the ECL for "-No signature-" and add trusted capabilities 
	 * 
	 * @param type ECL type
	 * @param capabilities capabilities to trust
	 * @param sessionOnly true to not permanently change the ECL
	 * @return new capabilities
	 */
	public static EnumSet<ECLCapability> trustNoSignatureUser(ECLType type, EnumSet<ECLCapability> capabilities, boolean sessionOnly) {
		return internalTrustSigner(null, type, capabilities, sessionOnly);
	}

	/**
	 * Method to modify the ECL for the signer of the specified note and add trusted
	 * capabilities
	 * 
	 * @param note signed note (we read $Signature internally)
	 * @param type ECL type
	 * @param capabilities capabilities to trust
	 * @param sessionOnly true to not permanently change the ECL
	 * @return new capabilities
	 */
	public static EnumSet<ECLCapability> trustSignerOfNote(NotesNote note, ECLType type, EnumSet<ECLCapability> capabilities, boolean sessionOnly) {
		if (note==null)
			throw new NullPointerException("Note cannot be null");
		
		return internalTrustSigner(note, type, capabilities, sessionOnly);
	}

	/**
	 * Internal method with shared code
	 * 
	 * @param note signed note (we read $Signature internally) or null to use "-No signature-" entry
	 * @param type ECL type
	 * @param capabilities capabilities to trust
	 * @param sessionOnly true to not permanently change the ECL
	 * @return new capabilities
	 */
	private static EnumSet<ECLCapability> internalTrustSigner(NotesNote note, ECLType type, EnumSet<ECLCapability> capabilities, boolean sessionOnly) {
		if (note!=null && note.isRecycled())
			throw new NotesError(0, "Note already recycled");
		
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		ShortByReference retwCapabilities = new ShortByReference();
		ShortByReference retwCapabilities2 = new ShortByReference();

		short wCapabilities = ECLCapability.toBitMaskNotExtendedFlags(capabilities);
		short wCapabilities2 = ECLCapability.toBitMaskExtendedFlags(capabilities);
		
		short result;
		
		if (NotesJNAContext.is64Bit()) {
			LongByReference rethCESCTX = new LongByReference();
			if (note!=null) {
				result = notesAPI.b64_CESCreateCTXFromNote((int) note.getHandle64(), rethCESCTX);
			}
			else {
				result = notesAPI.b64_CESGetNoSigCTX(rethCESCTX);
			}
			NotesErrorUtils.checkResult(result);				

			result = notesAPI.b64_ECLUserTrustSigner(rethCESCTX.getValue(), type.getTypeAsShort(),
					(short) (sessionOnly ? 1 : 0), wCapabilities, wCapabilities2, retwCapabilities, retwCapabilities2);
			NotesErrorUtils.checkResult(result);
		}
		else {
			IntByReference rethCESCTX = new IntByReference();
			if (note!=null) {
				result = notesAPI.b32_CESCreateCTXFromNote((int) note.getHandle64(), rethCESCTX);				
			}
			else {
				result = notesAPI.b32_CESGetNoSigCTX(rethCESCTX);
			}
			NotesErrorUtils.checkResult(result);

			result = notesAPI.b32_ECLUserTrustSigner(rethCESCTX.getValue(), type.getTypeAsShort(),
					(short) (sessionOnly ? 1 : 0), wCapabilities, wCapabilities2, retwCapabilities, retwCapabilities2);
			NotesErrorUtils.checkResult(result);
		}
		
		int retwCapabilitiesAsInt = (int) (retwCapabilities.getValue() & 0xffff);
		int retwCapabilities2AsInt = (int) (retwCapabilities2.getValue() & 0xffff);
		
		EnumSet<ECLCapability> set = toCapabilitySet(retwCapabilitiesAsInt, retwCapabilities2AsInt);

		return set;
	}
	
	/**
	 * Enum of available ECL capabilities
	 * 
	 * @author Karsten Lehmann
	 */
	public static enum ECLCapability {
		/** Access files (read/write/export/import)*/
		AccessFiles(NotesCAPI.ECL_FLAG_FILES, false),
		
		/** Access current db's docs/db */
		AccessCurrentDatabase(NotesCAPI.ECL_FLAG_DOCS_DBS, false),
		
		/** Access environ vars (get/set) */
		AccessEnvironmentVars(NotesCAPI.ECL_FLAG_ENVIRON, false),
		
		/** Access non-notes dbs (@DB with non "","Notes" first arg) */
		AccessNonNotesDatabases(NotesCAPI.ECL_FLAG_EXTERN_DBS, false),
		
		/** Access "code" in external systems (LS, DLLS, DDE) */
		AccessExternalSystems(NotesCAPI.ECL_FLAG_EXTERN_CODE, false),
		
		/** Access external programs (OLE/SendMsg/Launch) */
		AccessExternalPrograms(NotesCAPI.ECL_FLAG_EXTERN_PROGRAMS, false),
		
		/** Send mail (@MailSend) */
		SendMail(NotesCAPI.ECL_FLAG_SEND_MAIL, false),
		
		/** Access ECL */
		AccessECL(NotesCAPI.ECL_FLAG_ECL, false),
		
		/** Read access to other databases */
		ReadAccessOtherDatabases(NotesCAPI.ECL_FLAG_READ_OTHER_DBS, false),
		
		/** Write access to other databases */
		WriteAccessOtherDatabases(NotesCAPI.ECL_FLAG_WRITE_OTHER_DBS, false),
		
		/** Ability to export data (copy/print, etc) */
		ExportData(NotesCAPI.ECL_FLAG_EXPORT_DATA, false),
		
		//extended flags
		
		/** Access network programatically */
		AccessNetwork(NotesCAPI.ECL_FLAG_NETWORK, true),
		
		/** Property Broker Get */
		PropertyBrokerGet(NotesCAPI.ECL_FLAG_PROPERTY_GET, true),
		
		/** Property Broker Put */
		PropertyBrokerPut(NotesCAPI.ECL_FLAG_PROPERTY_PUT, true),
		
		/** Widget configuration */
		WidgetConfiguration(NotesCAPI.ECL_FLAG_WIDGETS, true),
		
		/** access to load Java */
		LoadJava(NotesCAPI.ECL_FLAG_LOADJAVA, true),
		
		//special flag
		ModifyECL(Integer.MAX_VALUE);
		
		private int m_flag;
		private boolean m_isWorkstationECL;
		
		private ECLCapability(short flag, boolean isWorkstationACL) {
			m_flag = (int) (flag & 0xffff);
			m_isWorkstationECL = isWorkstationACL;
		}

		/**
		 * Method to check if the capability is only available in the Notes Client
		 * 
		 * @return true if workstation ECL
		 */
		public boolean isWorkstationECL() {
			return m_isWorkstationECL;
		}
		
		private ECLCapability(int flag) {
			m_flag = flag;
		}

		public int getValue() {
			return m_flag;
		}
		
		public static short toBitMaskNotExtendedFlags(EnumSet<ECLCapability> capabilitySet) {
			int result = 0;
			if (capabilitySet!=null) {
				for (ECLCapability currCapability : values()) {
					if (!currCapability.isWorkstationECL()) {
						if (capabilitySet.contains(currCapability)) {
							result = result | currCapability.getValue();
						}
					}
				}
			}
			return (short) (result & 0xffff);
		}

		public static short toBitMaskExtendedFlags(EnumSet<ECLCapability> capabilitySet) {
			int result = 0;
			if (capabilitySet!=null) {
				for (ECLCapability currCapability : values()) {
					if (currCapability.isWorkstationECL()) {
						if (capabilitySet.contains(currCapability)) {
							result = result | currCapability.getValue();
						}
					}
				}
			}
			return (short) (result & 0xffff);
		}

	};

}
