package org.openmrs.module.messaging.web.dwr;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.messaging.MessageService;
import org.openmrs.module.messaging.MessagingAddressService;
import org.openmrs.module.messaging.MessagingService;
import org.openmrs.module.messaging.domain.Message;
import org.openmrs.module.messaging.domain.MessagingAddress;
import org.openmrs.module.messaging.domain.gateway.Protocol;
import org.openmrs.module.messaging.web.domain.MessageBean;

public class DWRModuleMessageService {
	
	private static Log log = LogFactory.getLog(DWRModuleMessageService.class);
	
	private MessageService messageService;
	private MessagingService messagingService;
	private MessagingAddressService addressService;
	
	/**
	* The max amount of time (in milliseconds) that 2 messages
	* can be apart before they are grouped separately.
	* Currently it is 1 hour.
	*/
	private static final long MAX_TIME_DISTANCE = 3600000;
	
	public DWRModuleMessageService(){
		messageService = Context.getService(MessageService.class);
		messagingService = Context.getService(MessagingService.class);
		addressService = Context.getService(MessagingAddressService.class);
	}
	
	/**
	 * Returns all messages to or from a patient in the form of a list of {@link MessageBean}s.
	 * @param patientId
	 * @return
	 */
	public List<MessageBean> getMessagesForPatient(Integer patientId){
//		//retreive the patient
//		Patient p = Context.getService(PatientService.class).getPatient(patientId);
//		List<MessageBean> results = new ArrayList<MessageBean>();
//		List<Message> messages = messageService.getMessagesToOrFromPerson(p);
//		MessageBean messageBean = null;
//		Map<String,Integer> colorNumbers = new HashMap<String,Integer>();
//		//holds the last used color number
//		int colorNumber = 0;
//		//holds the time (in milliseconds) of the last message
//		long lastTime = 0;
//		//holds the last used time row Id 
//		int timeId = -1;
//		for(Message message: messages){
//			//create the new message bean
//			messageBean = new MessageBean(message);
//			messageBean.setFromOpenMRS(message.getTo() != null && message.getTo().size() > 0 && message.getToPeople().contains(p));
//			//set the proper 'color number'
//			if(messageBean.isFromOpenMRS() && !colorNumbers.containsKey(message.getOrigin())){
//				colorNumbers.put(message.getOrigin(), colorNumber);
//				messageBean.setColorNumber(colorNumber++);
//			}else if(messageBean.isFromOpenMRS()){
//				messageBean.setColorNumber(colorNumbers.get(message.getOrigin()));
//			}
//			//if this message was sent too far apart from the last one,
//			//insert a date marker (empty MessageBean w/Date)
//			if(isTooFarApart(lastTime,message.getDate())){
//				MessageBean mb = new MessageBean();
//				mb.setDateAndTime(message.getDate());
//				mb.setId(timeId--);
//				results.add(mb);
//				lastTime = message.getDate().getTime();
//			}
//			results.add(messageBean);
//		}
		return null;
	}
	
	private boolean isTooFarApart(long lastTime, Date thisTime){
		return Math.abs(thisTime.getTime() - lastTime) > MAX_TIME_DISTANCE;
	}
	
	public String sendMessage(String content, String toAddressString, String subject, boolean isFromCurrentUser){
		Message message = null;
		Set<MessagingAddress> toAddresses = new HashSet<MessagingAddress>();
		MessagingAddress from = null;
		Class<? extends Protocol> protocolClass;
		//first we see if the addresses are already in the system
		String[] addresses = toAddressString.split(",");
		for(String s: addresses){
			String adrString = null;
			try{
				adrString = s.substring(s.indexOf("<")+1, s.indexOf(">"));
			}catch(Throwable t){
				continue;
			}
			Class<? extends Protocol> clazz = messagingService.getProtocolByAbbreviation(adrString.split(":")[0]).getClass();
			List<MessagingAddress> existingAddresses = addressService.findMessagingAddresses(adrString.split(":")[1], clazz, null);
			if(existingAddresses.size() != 1){
				return "Could not find address "+ s;
			}else{
				toAddresses.add(existingAddresses.get(0));
			}
		}
		if(toAddresses.size() < 1){
			return "No addresses entered";
		}
		protocolClass = ((MessagingAddress) toAddresses.toArray()[0]).getProtocol();
		for(MessagingAddress adr: toAddresses){
			if(protocolClass != adr.getProtocol()){
				return "Cannot currently send to different types of addresses.";
			}
		}
		//here we have a list of previously existing messaging addresses that all use the same protocol
		if(isFromCurrentUser){
			List<MessagingAddress> fromAdrResults = addressService.findMessagingAddresses("", protocolClass, Context.getAuthenticatedUser().getPerson());
			if(fromAdrResults.size() < 1 ){
				return "Cannot send from authenticated user - no from address";
			}else{
				from = fromAdrResults.get(0);
			}
		}
		try{
			message = messagingService.getProtocolByClass(protocolClass).createMessage(content, toAddresses, from);
		}catch(Exception e){
			return "Could not create message";
		}
		message.setSubject(subject!=null?subject:"");
		//if it is possible to send the message, do so
		if (!messagingService.canSendToProtocol(messagingService.getProtocolByClass(protocolClass))) {
			return "There is not currently a gateway running that can send that type of message.";
		} else {
			messagingService.sendMessage(message);
			return null;
		}
	}
	
	public List<MessageBean> getMessagesForAuthenticatedUser(Integer pageNumber, boolean to){
		return getMessagesForAuthenticatedUserWithPageSize(pageNumber, 10, to);
	}
	
	public List<MessageBean> getMessagesForAuthenticatedUserWithPageSize(Integer pageNumber, Integer pageSize, boolean to){
		return getMessagesForPerson(pageNumber,pageSize, Context.getAuthenticatedUser().getPerson().getId(),to);
	} 
	
	public List<MessageBean> getMessagesForPerson(Integer pageNumber, Integer pageSize, Integer personId, boolean to){
		List<MessageBean> beans = new ArrayList<MessageBean>();
		List<Message> messages = messageService.getMessagesForPersonPaged(pageNumber, pageSize, personId, to);
		for(Message m: messages){
			beans.add(new MessageBean(m));
		}
		return beans;
	}
}