package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cart_items")
data class CartItem(
    @PrimaryKey val id: String, // format: "vendorPrefix_productId"
    val productId: String,
    val productName: String,
    val category: String,
    val originalPrice: Double,
    val discountPrice: Double,
    val quantity: Int,
    val vendorId: String,
    val vendorName: String,
    // Real-time custom-add updates & reviews
    val dryCleaningType: String = "Standard Wash & Fold", // "Standard Wash & Fold", "Silk Delicate Care", "Heavy Bead Embroidery"
    val userNotes: String = "",
    val reviewRating: Int = 5
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val phone: String, // mobile phone acts as profile key
    val name: String,
    val email: String,
    val roles: String = "CUSTOMER", // comma separated roles: "CUSTOMER,VENDOR"
    val isGoogleSignedIn: Boolean = false,
    val referralCodeUsed: String = "",
    val signupTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_addresses")
data class SavedAddress(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String, // Home, Office, Other
    val addressLine: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "order_records")
data class OrderRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vendorName: String,
    val itemsSummary: String,
    val totalPrice: Double,
    val pickupSlot: String,
    val deliverySlot: String,
    val paymentMethod: String,
    val status: String, // Active, Placed, In Laundry, Washing, Ironing, Out for Delivery, Delivered
    val weightKg: Double = 0.0,
    val verifiedItemCount: Int = 0,
    val bagId: String = "",
    val userNotes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "support_messages")
data class SupportMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "User", "Support", "AI_Assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ApnaDhobiDao {
    // Cart operations
    @Query("SELECT * FROM cart_items")
    fun getCartItemsFlow(): Flow<List<CartItem>>

    @Query("SELECT * FROM cart_items")
    suspend fun getCartItems(): List<CartItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItem)

    @Update
    suspend fun updateCartItem(item: CartItem)

    @Delete
    suspend fun deleteCartItem(item: CartItem)

    @Query("DELETE FROM cart_items WHERE id = :itemId")
    suspend fun deleteCartItemById(itemId: String)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()

    // Address operations
    @Query("SELECT * FROM saved_addresses ORDER BY id DESC")
    fun getAddressesFlow(): Flow<List<SavedAddress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddress(address: SavedAddress)

    @Query("DELETE FROM saved_addresses WHERE id = :addressId")
    suspend fun deleteAddressById(addressId: Int)

    // Order operations
    @Query("SELECT * FROM order_records ORDER BY timestamp DESC")
    fun getOrdersFlow(): Flow<List<OrderRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderRecord)

    @Query("UPDATE order_records SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Int, status: String)

    @Query("UPDATE order_records SET weightKg = :weight, verifiedItemCount = :count, bagId = :bagId WHERE id = :orderId")
    suspend fun updateOrderDetails(orderId: Int, weight: Double, count: Int, bagId: String)

    @Query("DELETE FROM order_records")
    suspend fun clearAllOrders()

    // Chat operations
    @Query("SELECT * FROM support_messages ORDER BY timestamp ASC")
    fun getMessagesFlow(): Flow<List<SupportMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: SupportMessage)

    // User Profile operations
    @Query("SELECT * FROM user_profiles")
    fun getAllUserProfilesFlow(): Flow<List<UserProfile>>

    @Query("SELECT * FROM user_profiles WHERE phone = :phone")
    suspend fun getUserProfileByPhone(phone: String): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE email = :email")
    suspend fun getUserProfileByEmail(email: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("DELETE FROM user_profiles")
    suspend fun clearAllUserProfiles()
}

@Database(
    entities = [CartItem::class, SavedAddress::class, OrderRecord::class, SupportMessage::class, UserProfile::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apnaDhobiDao(): ApnaDhobiDao
}
