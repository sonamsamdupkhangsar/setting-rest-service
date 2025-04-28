package me.sonam.friendship;


import me.sonam.friendship.persist.entity.Friendship;
import me.sonam.webclients.user.User;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.HashSet;

import java.util.Set;
import java.util.UUID;

public class FriendshipTest {
	private static final Logger LOG = LoggerFactory.getLogger(FriendshipTest.class);

	@Test
	public void constructor() {
		LocalDateTime date = LocalDateTime.now();
		UUID uuid = UUID.randomUUID();
		User jack = new User(uuid,"jack mack");

		User ana = new User(uuid, "ana lana");

		Friendship friendship = new Friendship(date, date, jack.getId(), ana.getId(), false);

		Assert.assertEquals(friendship.getRequestSentDate(), date);
		Assert.assertEquals(friendship.getResponseSentDate(), date);
		Assert.assertEquals(friendship.getUserId(), jack.getId());
		Assert.assertEquals(friendship.getFriendId(), ana.getId());

		Assert.assertNotEquals(friendship.getId(), UUID.randomUUID());
	}
	
	@Test
	public void getterSetter() {
		LocalDateTime date = LocalDateTime.now();
		UUID uuid = UUID.randomUUID();

		User jack = new User(uuid,"jack mack");
		User ana = new User(uuid,"ana lana");


		Friendship friendship = new Friendship();
		friendship.setId(uuid);
		friendship.setResponseSentDate(date);
		friendship.setRequestSentDate(date);
		friendship.setUserId(jack.getId());
		friendship.setFriendId(ana.getId());

		Assert.assertEquals(friendship.getId(), uuid);
		Assert.assertEquals(friendship.getRequestSentDate(), date);
		Assert.assertEquals(friendship.getResponseSentDate(), date);
		Assert.assertEquals(friendship.getUserId(), jack.getId());
		Assert.assertEquals(friendship.getFriendId(), ana.getId());

		Assert.assertNotEquals(friendship.getId(), UUID.randomUUID());
		User ana2 = new User(uuid,"ana lana");
		Assert.assertEquals(friendship.getFriendId(), ana2.getId());
		ana2.setId(UUID.randomUUID());
		Assert.assertNotEquals(friendship.getFriendId(), ana2.getId());
		
	}
	
	@Test
	public void equals() {
		LocalDateTime date = LocalDateTime.now();
		
		User jack = new User(UUID.randomUUID(), "jack mack");
		User ana = new User(UUID.randomUUID(), "ana lana");
		User manu = new User(UUID.randomUUID(), "Manu Garewal");

		Friendship jackAndAna = new Friendship(date,date, jack.getId(), ana.getId(), false);
		Friendship jackAndAna2 = new Friendship(date,date, jack.getId(), ana.getId(), false);
		jackAndAna2.setId(jackAndAna.getId());
		
		Assert.assertTrue(jackAndAna.equals(jackAndAna2));
		Assert.assertTrue(jackAndAna.toString().equals(jackAndAna2.toString()));
		
		jackAndAna2.setId(UUID.randomUUID());
		Assert.assertFalse(jackAndAna.equals(jackAndAna2));		

		jackAndAna2.setId(null);
		jackAndAna.setId(null);
		Assert.assertTrue(jackAndAna.equals(jackAndAna2));
	}
	@Test
	public void contains() {
		LocalDateTime date = LocalDateTime.now();
		
		User jack = new User(UUID.randomUUID(), "jack mack");
		User ana = new User(UUID.randomUUID(), "ana lana");
		User manu = new User(UUID.randomUUID(), "Manu Garewal");

		Friendship jackAndAna = new Friendship(date,date, jack.getId(), ana.getId(), false);
		Friendship jackAndManu = new Friendship(date,date, jack.getId(), manu.getId(), false);
		
		Set<Friendship> set = new HashSet<>();
		set.add(jackAndAna);
		set.add(jackAndManu);



		Friendship jackAndAnaCopy = new Friendship(date,date, jack.getId(), ana.getId(), false);
		Friendship jackAndManuCopy = new Friendship(date,date, jack.getId(), manu.getId(), false);
		jackAndManuCopy.setId(jackAndManu.getId());
		jackAndAnaCopy.setId(jackAndAna.getId());

		Assert.assertTrue(set.contains(jackAndManuCopy));
		Assert.assertTrue(set.contains(jackAndAnaCopy));
		jackAndAnaCopy.setId(UUID.randomUUID());
		Assert.assertFalse(set.contains(jackAndAnaCopy));
	}
}
